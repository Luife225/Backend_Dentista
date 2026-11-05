import io
import sys
import tempfile
import unittest
import wave
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
from dental import parse, revise
from knowledge import DATA, Knowledge
from luna import LunaClassifier, LunaQuota, safe_help_query
from patient import analyze
from appointment import analyze_appointment
from speech import SpeechError, validate_audio
import server


class CoroVozPythonTest(unittest.TestCase):
    def test_health_reports_model_readiness_without_service_token(self):
        class Probe:
            path = "/health"
            response = None

            def respond(self, status, payload):
                self.response = (status, payload)

            def authorized(self):
                raise AssertionError("Health must not require the service token")

        for installed, expected in ((True, 200), (False, 503)):
            with patch.object(server, "installed", return_value=installed):
                probe = Probe()
                server.Handler.do_GET(probe)
                self.assertEqual((expected, {"ready": installed}), probe.response)

    def test_every_tooth_starts_a_structured_partial_proposal(self):
        for quadrant in range(1, 5):
            for index in range(1, 9):
                piece = quadrant * 10 + index
                for prefix in ("pieza", "diente"):
                    partial = parse(f"{prefix} {piece}")
                    self.assertEqual({"piece": piece, "condition": None, "surface": "GENERAL", "note": ""}, partial["draft"])
                    self.assertIsNone(partial["proposal"])
                    self.assertTrue(partial["incomplete"])
        self.assertIsNone(parse("pieza 99")["draft"])
        self.assertIsNone(parse("pieza 15 y 16")["draft"])

    def test_partial_proposal_accepts_any_field_order(self):
        partial = parse("pieza quince")["draft"]
        partial = revise("observaciones: control en 2 semanas", "note", partial)["draft"]
        self.assertEqual(15, partial["piece"])
        self.assertIsNone(partial["condition"])
        partial = revise("cara mesial", "surface", partial)["draft"]
        self.assertEqual("MESIAL", partial["surface"])
        ready = revise("corona", "condition", partial)["proposal"]
        self.assertEqual({"piece": 15, "condition": "CORONA", "surface": "MESIAL", "note": "control en 2 semanas"}, ready)
        partial = parse("pieza 15 cara distal. Observación: diente caído")["draft"]
        self.assertIsNone(partial["condition"])
        self.assertEqual("CORONA", revise("condición corona", "condition", partial)["proposal"]["condition"])

    def test_proposal_edit_changes_only_the_requested_field(self):
        base = parse("pieza 15 corona cara mesial. Observaciones: diente caído")["proposal"]
        self.assertEqual("diente caído", base["note"])
        for field, text, expected in [("piece", "dieciséis", 16), ("condition", "restauración", "RESTAURADO"),
                                      ("note", "observaciones: controlar en 2 semanas", "controlar en 2 semanas"),
                                      ("surface", "cara distal", "DISTAL")]:
            edited = revise(text, field, base)["proposal"]
            self.assertIsNotNone(edited, (field, text))
            self.assertEqual(expected, edited[field])
            for key in base:
                if key != field:
                    self.assertEqual(base[key], edited[key], (field, key))
        for field, text in [("piece", "15 y 16"), ("piece", "99"), ("condition", "pieza 16 caries"),
                            ("condition", "caries cara distal"), ("condition", "no tiene caries"),
                            ("condition", "sano"), ("note", "observaciones"), ("note", "x" * 301)]:
            self.assertIsNone(revise(text, field, base)["proposal"], (field, text))
        self.assertEqual(18, revise("18", "piece", {**base, "piece": None})["proposal"]["piece"])
        self.assertEqual("CARIES", revise("caries", "condition", {**base, "condition": None})["proposal"]["condition"])
        self.assertEqual("corona", revise("corrona", "condition", base)["suggestions"][0]["value"])

    @classmethod
    def setUpClass(cls):
        cls.knowledge = Knowledge()

    def test_independent_questions_and_paraphrases(self):
        rows = [line.split("|", 1) for line in (DATA / "evaluation.tsv").read_text(encoding="utf-8").splitlines()
                if line and not line.startswith("#")]
        for expected, question in rows + [
            ("RADIOGRAFIAS", "¿Puedo subir una radiografía a la ficha?"),
            ("CITA_CAMBIO", "¿En qué pantalla se corrige la fecha de una reserva?"),
        ]:
            with self.subTest(question=question):
                self.assertEqual(expected, self.knowledge.answer(question, "ODONTOLOGO")["intent"])

    def test_unknown_and_role_restrictions(self):
        for phrase in ("qué medicina tomo para dolor de muela", "paciente con dolor, qué medicamento dar", "necesito tratamiento para caries"):
            with self.subTest(phrase=phrase):
                clinical = self.knowledge.answer(phrase, "ODONTOLOGO")
                self.assertFalse(clinical["understood"])
                self.assertEqual([], clinical["suggestions"])
                self.assertIn("no a diagnosticar", clinical["answer"])
        self.assertEqual("", self.knowledge.answer("cómo llenar el odontograma", "RECEPCIONISTA")["route"])

    def test_short_questions_offer_role_aware_choices(self):
        for phrase in ("cita", "paciente", "archivo", "odontograma", "agenda"):
            with self.subTest(phrase=phrase):
                result = self.knowledge.answer(phrase, "ODONTOLOGO")
                self.assertFalse(result["understood"])
                self.assertEqual("UNKNOWN", result["intent"])
                self.assertGreaterEqual(len(result["suggestions"]), 2)
                self.assertLessEqual(len(result["suggestions"]), 3)
                selected = result["suggestions"][0]["intent"]
                self.assertEqual(selected, self.knowledge.answer(phrase, "ODONTOLOGO", selected)["intent"])

        receptionist = self.knowledge.answer("odontograma", "RECEPCIONISTA")
        self.assertEqual([], receptionist["suggestions"])
        self.assertEqual("", self.knowledge.answer("odontograma", "RECEPCIONISTA", "ODONTOGRAMA")["route"])
        with self.assertRaises(ValueError):
            self.knowledge.answer("cita", "ODONTOLOGO", "NO_EXISTE")

    def test_dental_safety_and_context(self):
        proposal = parse("Caries en diente treinta y seis cara oclusal. Observación: control en 2 semanas.")
        self.assertEqual(36, proposal["proposal"]["piece"])
        self.assertEqual("control en 2 semanas.", proposal["proposal"]["note"])
        self.assertEqual(14, parse("Selecciona el catorce")["selectedPiece"])
        self.assertEqual(14, parse("caries en ese diente", 14)["proposal"]["piece"])
        for phrase in ("no hay caries en 14", "elimina caries en 14", "caries en 14 y 15", "caries profunda en 14"):
            with self.subTest(phrase=phrase):
                self.assertIsNone(parse(phrase)["proposal"])

    def test_dental_natural_dictation_covers_every_odontogram_condition(self):
        examples = {
            "el diente número treinta y seis está sano": (36, "SANO"),
            "diente 3 6 tiene caries": (36, "CARIES"),
            "la pieza catorce está restaurada": (14, "RESTAURADO"),
            "diente 21 con corona": (21, "CORONA"),
            "el diente treinta seis se encuentra extraído": (36, "EXTRAIDO"),
            "diente 31 con endodoncia": (31, "ENDODONCIA"),
            "pieza 24 implantada": (24, "IMPLANTE"),
            "pieza cuarenta y ocho fracturada": (48, "FRACTURA"),
        }
        for phrase, (piece, condition) in examples.items():
            with self.subTest(phrase=phrase):
                proposal = parse(phrase)["proposal"]
                self.assertIsNotNone(proposal)
                self.assertEqual((piece, condition), (proposal["piece"], proposal["condition"]))
        self.assertTrue(parse("diente número treinta y seis")["incomplete"])
        self.assertIsNone(parse("pieza 36 sana y extraída")["proposal"])
        self.assertIsNone(parse("pieza 36 sin caries")["proposal"])
        self.assertIsNone(parse("pieza 36 sana observación control")["proposal"])
        self.assertIsNone(parse("pieza 36 extraída cara oclusal")["proposal"])

    def test_patient_analysis_never_returns_clinical_fields(self):
        self.assertTrue(analyze("Busca a Juan Pérez")["handles"])
        self.assertEqual(["juan", "perez"], analyze("Busca a Juan Pérez")["search"])
        self.assertTrue(analyze("alergias del paciente Ana")["restricted"])
        self.assertFalse(analyze("cómo cambiar teléfono del paciente")["handles"])

    def test_natural_patient_questions_keep_fields_and_names(self):
        for phrase, field, search in [
            ("Quiero saber si Carlos Gamboa presenta radiografías", "radiographs", ["carlos", "gamboa"]),
            ("Sofía Castillo tiene alguna alergia registrada", "allergies", ["sofia", "castillo"]),
            ("Bueno, un momento, quería preguntarte si el paciente Castillo tiene alergias registradas", "allergies", ["castillo"]),
            ("¿Y sus documentos?", "documents", []), ("¿Y su historial?", "history", []),
            ("Diagnóstico registrado del paciente Carlos Gamboa", "diagnosis", ["carlos", "gamboa"]),
        ]:
            with self.subTest(phrase=phrase):
                plan = analyze(phrase)
                self.assertTrue(plan["handles"])
                self.assertEqual(field, plan["field"])
                self.assertEqual(search, plan["search"])
        self.assertEqual(["allergies", "medications"], analyze("alergias y medicamentos de Carlos Gamboa")["fields"])
        self.assertFalse(analyze("¿Cómo consulto la historia clínica?")["handles"])
        self.assertFalse(analyze("Qué tratamiento debería dar al paciente")["handles"])

    def test_conversational_dental_intro_and_anatomy(self):
        value = parse("Ok, vamos a iniciar, he encontrado en el masilar superior, diente 18, una caries.")
        self.assertEqual({"piece": 18, "condition": "CARIES", "surface": "GENERAL", "note": ""}, value["proposal"])
        self.assertIsNone(parse("En maxilar inferior, diente 18, caries")["proposal"])
        self.assertIsNone(parse("En maxilar superior, diente 18, posible caries")["proposal"])
        self.assertEqual("TELEODONTOLOGIA", self.knowledge.answer("tele odontología", "ODONTOLOGO")["intent"])
        self.assertEqual("", self.knowledge.answer("tele odontología", "RECEPCIONISTA")["route"])

    def test_company_overviews_and_patient_pronouns(self):
        for phrase in ("qué es coronyx", "cuéntame sobre Coronyx", "qué es la empresa Coronix", "háblame de la plataforma Coronyx"):
            self.assertEqual("CORONYX", self.knowledge.answer(phrase, "ODONTOLOGO")["intent"], phrase)
        for phrase in ("ella tiene alergias", "y él presenta alergias", "alergias de ella", "¿y las alergias de esa misma paciente?"):
            plan = analyze(phrase)
            self.assertEqual([], plan["search"], phrase)
            self.assertEqual("allergies", plan["field"])
        self.assertTrue(analyze("y ella")["handles"])
        self.assertEqual(["ana", "perez"], analyze("y Ana Pérez tiene alergias")["search"])

    def test_dental_variants_for_all_conditions_and_selected_tooth(self):
        examples = {
            "SANO": ["sana", "saludable", "íntegro"],
            "CARIES": ["caries", "cariada", "lesión cariosa"],
            "RESTAURADO": ["restauración", "restaurada", "se restauró", "obturación", "empaste"],
            "CORONA": ["corona", "coronada"],
            "EXTRAIDO": ["extracción", "extraída", "se extrajo"],
            "ENDODONCIA": ["endodoncia", "endodonciado", "tratamiento de conductos"],
            "IMPLANTE": ["implante", "implantada", "se implantó"],
            "FRACTURA": ["fractura", "fracturada", "se fracturó", "se rompió"],
        }
        for condition, forms in examples.items():
            for phrase in forms:
                with self.subTest(phrase=phrase):
                    proposal = parse(phrase, 18)["proposal"]
                    self.assertIsNotNone(proposal)
                    self.assertEqual((18, condition), (proposal["piece"], proposal["condition"]))
        self.assertIsNone(parse("restauración")["proposal"])
        self.assertIsNone(parse("restauración", 99)["proposal"])
        self.assertEqual(26, parse("restauración en pieza 26", 18)["proposal"]["piece"])
        self.assertIsNone(parse("vamos a restaurar el diente 18")["proposal"])

    def test_dental_clarifications_preserve_slots_and_only_suggest_typos(self):
        for typo, expected in [("sanno", "sano"), ("cariess", "caries"), ("restaracion", "restauracion"),
                               ("corrona", "corona"), ("extraiddo", "extraido"), ("endodonsia", "endodoncia"),
                               ("inplante", "implante"), ("fracturra", "fractura")]:
            response = parse("diente 18 " + typo)
            self.assertIsNone(response["proposal"], typo)
            self.assertIn(expected, [s["value"] for s in response["suggestions"]], typo)
            self.assertEqual(18, response["contextPiece"])
            self.assertIsNotNone(parse(response["fragment"] + " " + expected)["proposal"])
        unknown = parse("diente 18 cara oclusal restaracion. Observación: control en 2 semanas")
        self.assertEqual("pieza 18 cara oclusal. Observación: control en 2 semanas", unknown["fragment"])
        acknowledged = parse("el diente está bien", 18)
        self.assertTrue(acknowledged["incomplete"])
        self.assertIsNone(acknowledged["proposal"])
        self.assertEqual("pieza 18", acknowledged["fragment"])
        self.assertEqual(8, len(acknowledged["suggestions"]))
        conflict = parse("diente 18 caries o corona cara mesial")
        self.assertEqual("pieza 18 cara mesial", conflict["fragment"])
        self.assertEqual(2, len(conflict["suggestions"]))
        negative = parse("no tiene caries", 18)
        self.assertNotIn("caries", negative["fragment"])
        self.assertIsNone(negative["proposal"])

    def test_appointment_draft_requires_unambiguous_time_and_valid_date(self):
        now = datetime(2026, 10, 24, 18, tzinfo=timezone.utc)
        plan = analyze_appointment("Agenda una cita para Carlos Gamboa mañana a las tres de la tarde por limpieza", now)
        self.assertEqual(["carlos", "gamboa"], plan["search"])
        self.assertEqual("2026-10-25", plan["appointment"]["date"])
        self.assertEqual("15:00", plan["appointment"]["time"])
        self.assertEqual("", plan["appointment"]["clarification"])
        self.assertEqual("limpieza", plan["appointment"]["reason"])
        self.assertIsNone(analyze_appointment("Cómo agendo una cita", now))
        for phrase in ["Crea una cita mañana a las 3", "Crea una cita el 31/02/2027 a las 15:00",
                       "Crea una cita el 20/10/2026 a las 15:00", "Crea una cita mañana a las 25:00",
                       "Crea una cita mañana a las 15:00 o a las 16:00", "Crea una cita"]:
            with self.subTest(phrase=phrase):
                self.assertTrue(analyze_appointment(phrase, now)["appointment"]["clarification"])

    def test_private_queries_do_not_use_luna(self):
        for text in ["alergias de sofia castillo", "radiografías de carlos gamboa", "historial de castillo"]:
            self.assertFalse(safe_help_query(text))

    def test_audio_validation_without_model(self):
        def wav(rate=16000, sample=b"\x00\x20"):
            buffer = io.BytesIO()
            with wave.open(buffer, "wb") as output:
                output.setnchannels(1); output.setsampwidth(2); output.setframerate(rate)
                output.writeframes(sample * 16000)
            return buffer.getvalue()
        validate_audio(wav())
        for audio in (wav(44100), wav(sample=b"\x00\x00"), b"invalid"):
            with self.subTest(audio=audio[:8]):
                with self.assertRaises(SpeechError):
                    validate_audio(audio)

    def test_luna_classifies_only_safe_general_help_with_fake_transport(self):
        calls = []
        def fake_api(payload, key):
            calls.append((payload, key))
            return {"status": "completed", "output": [{"content": [{"type": "output_text", "text": '{"intent":"AGENDA_FILTROS"}'}]}],
                    "usage": {"input_tokens": 1200, "output_tokens": 40}}
        with tempfile.TemporaryDirectory() as directory:
            quota = LunaQuota(Path(directory) / "usage.sqlite", 50, Decimal("1"))
            off = LunaClassifier(enabled=False, api_key="fake-test-key", quota=quota, transport=fake_api)
            self.assertIsNone(off.classify("Quiero consultar el calendario", "ODONTOLOGO", self.knowledge, 7, 2))
            no_credentials = LunaClassifier(enabled=True, api_key="", quota=quota, transport=fake_api)
            self.assertIsNone(no_credentials.classify("Quiero consultar el calendario", "ODONTOLOGO", self.knowledge, 7, 2))
            self.assertEqual([], calls)
            model = LunaClassifier(enabled=True, api_key="fake-test-key", quota=quota, transport=fake_api)
            self.assertEqual("AGENDA_FILTROS", model.classify("Quiero consultar el calendario", "ODONTOLOGO", self.knowledge, 7, 2))
            self.assertEqual("gpt-6-luna", calls[0][0]["model"])
            self.assertIs(calls[0][0]["store"], False)
            self.assertEqual("none", calls[0][0]["reasoning"]["effort"])
            self.assertEqual("fake-test-key", calls[0][1])
            self.assertIsNone(model.classify("cita", "ODONTOLOGO", self.knowledge, 7, 2))
            self.assertIsNone(model.classify("archivo de Ana", "ODONTOLOGO", self.knowledge, 7, 2))
            self.assertEqual(1, len(calls))
        self.assertFalse(safe_help_query("paciente con dolor, qué medicamento dar"))
        self.assertFalse(safe_help_query("archivo de ana"))

    def test_luna_quota_limits_clinic_and_total_and_persists(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "usage.sqlite"
            quota = LunaQuota(path, 2, Decimal("1"))
            first = quota.reserve(2, 7)
            second = quota.reserve(2, 7)
            self.assertIsNotNone(first)
            self.assertIsNotNone(second)
            self.assertIsNone(LunaQuota(path, 2, Decimal("1")).reserve(2, 7))
            quota.finish(first, {"input_tokens": 1000, "output_tokens": 100})
            self.assertIsNotNone(quota.reserve(2, 8))
            tight = LunaQuota(Path(directory) / "tight.sqlite", 50, Decimal("0.001"))
            self.assertIsNotNone(tight.reserve(2, 7))
            self.assertIsNone(tight.reserve(2, 8))
            clinic_cap = LunaQuota(Path(directory) / "clinic.sqlite", 50, Decimal("0.001"), Decimal("3"))
            october = datetime(2026, 10, 23, 15, tzinfo=timezone.utc)
            november = datetime(2026, 11, 23, 15, tzinfo=timezone.utc)
            self.assertIsNotNone(clinic_cap.reserve(2, 7, october))
            self.assertIsNone(clinic_cap.reserve(2, 8, october))
            self.assertIsNotNone(clinic_cap.reserve(3, 7, october))
            self.assertIsNotNone(clinic_cap.reserve(2, 7, november))
            global_cap = LunaQuota(Path(directory) / "global.sqlite", 50, Decimal("1"), Decimal("0.001"))
            self.assertIsNotNone(global_cap.reserve(2, 7, october))
            self.assertIsNone(global_cap.reserve(3, 8, october))

    def test_general_help_uses_luna_choice_but_keeps_reviewed_answer(self):
        class FakeLuna:
            def classify(self, text, role, knowledge, user_id, clinic_id):
                self.identity = (user_id, clinic_id)
                return "AGENDA_FILTROS"
        fake = FakeLuna()
        with patch.object(server, "LUNA", fake):
            result = server.general_answer("quiero ver una cita", "ODONTOLOGO", user_id=7, clinic_id=2)
            self.assertEqual("AGENDA_FILTROS", result["intent"])
            self.assertEqual(self.knowledge.articles["AGENDA_FILTROS"][4], result["answer"])
            self.assertEqual((7, 2), fake.identity)
            self.assertEqual("CITA_NUEVA", server.general_answer("cita", "ODONTOLOGO", "CITA_NUEVA")["intent"])


if __name__ == "__main__":
    unittest.main()
