"""CoroVoz help classifier. The clinical data never enters the training corpus."""

from __future__ import annotations

import math
import re
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

DATA = Path(__file__).parent / "data"
UNKNOWN = "¿Qué necesitas hacer en Coronyx? Puedes preguntarme por citas, pacientes, documentos, odontogramas, acceso o el micrófono. Si buscas una cita concreta, indícame el nombre o DNI del paciente."
CLINICAL_SCOPE = "Puedo ayudarte a usar Coronyx, pero no a diagnosticar ni a indicar medicamentos o tratamientos. Si quieres gestionar una cita, dime si deseas agendarla o buscarla."
CLINICAL_TERMS = re.compile(r"\b(?:dolor|duele|doliendo|medicament\w*|medicin\w*|antibiotic\w*|analgesic\w*|tratamient\w*|diagnos\w*|recet\w*|infeccion\w*|sintom\w*)\b")
STOP = set("a al algo ayuda ayudame como con cual de del donde el ella en es esta este esto hacer hay la las lo los me mi mis necesito no para por puedo que quiero se si sistema sobre su tengo un una unos unas ver y yo podria quisiera favor explicame dime puedes veo hago cuales explicas explicar hace funcion funciones estan persona personas usa utilizar utilizaria antes mas cada porque logro ya he ningun ninguno manana hoy".split())
ALIASES = {}
for canonical, forms in {
    "odontologo": "doctor doctores profesional profesionales dentista dentistas odontologos",
    "abrir": "abro abre entro entrar accedo acceder iniciar inicio",
    "buscar": "busco busca busqueda encontrar encuentro localizar",
    "registrar": "registro registra registrado registrada registrando anadir agregar crear creo crea",
    "reservar": "reservo reserva programar programo agendar agendo agiendo",
    "cita": "citas turno turnos consulta consultas",
    "inactivo": "inactiva inactivas inactivos deshabilitado deshabilitada deshabilitados desactivado desactivada",
    "archivado": "archivada archivadas archivados archivar",
    "activar": "activo activa reactivo reactiva reactivar habilitar habilita",
    "desactivar": "desactivo desactiva bloquear bloquea deshabilitar",
    "editar": "edito edita actualizar actualizo actualiza corregir corrijo modificar modifico cambio cambiar",
    "costo": "cuesta cuestan dinero pago pagar pagos cobran cobra cobro",
    "esperar": "espere espera esperas",
    "responder": "responde responda respuesta respuestas",
    "rol": "roles tipo tipos", "acceso": "ingreso ingresar entrar login sesion",
    "expirada": "expirado expiro expira vencida vencido caducada",
    "archivo": "archivos adjunto adjuntos documento documentos",
    "subir": "subo cargar cargo carga adjuntar adjunto anexar",
    "confirmado": "confirmada confirmadas confirmados confirmar",
    "error": "rechaza rechazan rechazado rechazada invalido invalida errores",
    "guardado": "guardar guardo guarda guardados guardadas guardan",
    "interrumpir": "interrumpe interrumpa interrumpiendo corta corte cortar",
}.items():
    ALIASES.update({form: canonical for form in forms.split()})


def plain(text: str) -> str:
    value = unicodedata.normalize("NFD", text.lower())
    value = "".join(c for c in value if unicodedata.category(c) != "Mn")
    value = re.sub(r"[^a-z0-9 ]", " ", value)
    value = re.sub(r"\s+", " ", value).strip()
    for pattern, replacement in ((r"\bcoro voz\b", "corovoz"), (r"\bcoronix\b", "coronyx"),
                                 (r"\btele\s+odontologia\b", "teleodontologia"),
                                 (r"\bodonto\s+grama\b", "odontograma"),
                                 (r"\bradio\s+grafias?\b", "radiografias")):
        value = re.sub(pattern, replacement, value)
    return value


def tokens(text: str) -> set[str]:
    value = re.sub(r"\bya (?:esta )?registrad[oa]\b", "duplicado", plain(text))
    result = set()
    for term in value.split():
        if len(term) <= 1 or term in STOP:
            continue
        term = ALIASES.get(term, term)
        result.add(term[:-1] if len(term) > 4 and term.endswith("s") else term)
    return result


def normalize(vector: dict[str, float]) -> dict[str, float]:
    length = math.sqrt(sum(v * v for v in vector.values()))
    return {k: v / length for k, v in vector.items()} if length else vector


def dot(left: dict[str, float], right: dict[str, float]) -> float:
    return sum(v * right.get(k, 0) for k, v in left.items())


def one_edit(left: str, right: str) -> bool:
    if abs(len(left) - len(right)) > 1:
        return False
    i = j = edits = 0
    while i < len(left) and j < len(right):
        if left[i] == right[j]:
            i += 1
            j += 1
        else:
            edits += 1
            if edits > 1:
                return False
            if len(left) >= len(right):
                i += 1
            if len(right) >= len(left):
                j += 1
    return edits + len(left) - i + len(right) - j == 1


class Knowledge:
    def __init__(self) -> None:
        self.articles = {}
        for line in (DATA / "knowledge.tsv").read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#"):
                key, roles, route, label, title, answer = line.split("|", 5)
                if key in self.articles:
                    raise ValueError(f"Artículo duplicado: {key}")
                self.articles[key] = (roles, route, label, title, answer)
        examples = []
        for line in (DATA / "training.tsv").read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#"):
                intent, phrase = line.split("|", 1)
                examples.append((intent, tokens(phrase)))
        if {i for i, _ in examples} != set(self.articles):
            raise ValueError("Los artículos y el corpus de CoroVoz no coinciden")
        frequency = Counter(term for _, terms in examples for term in terms)
        self.idf = {term: math.log((1 + len(examples)) / (1 + count)) + 1 for term, count in frequency.items()}
        self.neighbors = defaultdict(list)
        centers = defaultdict(lambda: defaultdict(float))
        for intent, terms in examples:
            vector = normalize({t: self.idf[t] for t in terms})
            self.neighbors[intent].append(vector)
            for term, weight in vector.items():
                centers[intent][term] += weight
        self.centers = {k: normalize(dict(v)) for k, v in centers.items()}

    def answer(self, text: str, role: str, selected_intent: str | None = None) -> dict:
        if role not in {"ODONTOLOGO", "RECEPCIONISTA"}:
            raise PermissionError("Rol no autorizado")
        if selected_intent:
            article = self.articles.get(selected_intent)
            if article is None:
                raise ValueError("Opción desconocida")
            roles, route, label, title, response = article
            if roles != "*" and role not in roles.split(","):
                return answer(selected_intent, f"Esta función está reservada a {roles}. {response}", "", "", True)
            return answer(selected_intent, response, route, label, True)
        if CLINICAL_TERMS.search(plain(text)):
            return answer("UNKNOWN", CLINICAL_SCOPE, "", "", False)
        # An overview is a distinct intent, even with conversational filler or ASR spelling.
        overview = plain(text)
        if re.fullmatch(r"(?:(?:hola|oye|por favor|bueno|ahora|y) )*(?:(?:que es|que significa|para que sirve|que hace|hablame (?:de|sobre)|cuentame (?:de|sobre)|explicame|describe|informacion (?:de|sobre)) )?(?:(?:la empresa|el sistema|la plataforma|la aplicacion) )?coronyx(?: por favor)?", overview):
            return self.answer(text, role, "CORONYX")
        terms = tokens(text)
        corrected = set()
        for term in terms:
            if len(term) < 5 or term in self.idf:
                corrected.add(term)
            else:
                candidates = [known for known in self.idf if one_edit(term, known)][:2]
                corrected.add(candidates[0] if len(candidates) == 1 else term)
        query = normalize({t: self.idf[t] for t in corrected if t in self.idf})
        ranked = sorted(((0.8 * max(dot(query, n) for n in neighbors) + 0.2 * dot(query, self.centers[intent]), intent)
                         for intent, neighbors in self.neighbors.items()), reverse=True)
        first, intent = ranked[0]
        second = ranked[1][0] if len(ranked) > 1 else 0
        coverage = len(query) / len(corrected) if corrected else 0
        vague = len(corrected) == 1 and next(iter(corrected), "") in {"cita", "paciente", "archivo", "odontograma", "agenda"}
        uncertain = len(corrected) > 1 and len(query) < 2 or first < 0.36 or first - second < 0.035 or coverage < 0.45
        if vague or uncertain:
            suggestions = self.suggestions(ranked, role) if first >= 0.25 and coverage >= 0.35 else []
            if suggestions:
                return answer("UNKNOWN", "¿Te refieres a alguna de estas opciones? Pulsa una o di su número, por ejemplo «la primera».", "", "", False, suggestions)
            return answer("UNKNOWN", UNKNOWN, "", "", False)
        roles, route, label, title, response = self.articles[intent]
        if roles != "*" and role not in roles.split(","):
            return answer(intent, f"Esta función está reservada a {roles}. {response}", "", "", True)
        return answer(intent, response, route, label, True)

    def suggestions(self, ranked: list[tuple[float, str]], role: str) -> list[dict]:
        options = []
        for score, intent in ranked:
            if score < 0.22 or len(options) == 3:
                break
            roles, route, label, title, response = self.articles[intent]
            if roles != "*" and role not in roles.split(","):
                continue
            options.append({"intent": intent, "label": title})
        return options


def answer(intent: str, message: str, route: str, label: str, understood: bool, suggestions=None) -> dict:
    return {"intent": intent, "answer": message, "route": route, "targetLabel": label,
            "understood": understood, "patients": [], "patientId": None, "patientName": None,
            "suggestions": suggestions or []}
