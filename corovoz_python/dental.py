"""Conservative dental command parser: it never writes clinical data."""

import re
from difflib import SequenceMatcher
from knowledge import plain

TEETH = {10 * q + i for q in range(1, 5) for i in range(1, 9)}
CONDITIONS = {
    "SANO": "sano|sana|saludable|integro|integra", "CARIES": "caries|carie|cariado|cariada|cariosa|carioso|lesion cariosa",
    "RESTAURADO": "restaurado|restaurada|restauracion|restauraciones|obturacion|obturado|obturada|empaste|empastado|empastada|resina|se restauro|se obturo",
    "CORONA": "corona|coronado|coronada", "EXTRAIDO": "extraido|extraida|extraccion|extrajeron|se extrajo|sacado|sacada",
    "ENDODONCIA": "endodoncia|endodontico|endodontica|endodonciado|endodonciada|tratamiento de conducto|tratamiento de conductos|conducto",
    "IMPLANTE": "implante|implantado|implantada|se implanto",
    "FRACTURA": "fractura|fracturado|fracturada|roto|rota|quebrado|quebrada|se fracturo|se rompio",
}
LABELS = dict(zip(CONDITIONS, ("Sano", "Caries", "Restauración", "Corona", "Extraído", "Endodoncia", "Implante", "Fractura")))
WORDS = dict(zip(CONDITIONS, ("sano", "caries", "restauracion", "corona", "extraido", "endodoncia", "implante", "fractura")))
SURFACES = {"OCLUSAL": "oclusal", "VESTIBULAR": "vestibular|bucal", "LINGUAL": "lingual|palatina|palatino",
            "MESIAL": "mesial", "DISTAL": "distal", "GENERAL": "general|completo|completa"}
UNITS = ["", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho"]
TEENS = ["once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho"]
TWENTIES = ["veintiuno", "veintidos", "veintitres", "veinticuatro", "veinticinco", "veintiseis", "veintisiete", "veintiocho"]
NUMBERS = {}
for i in range(1, 9):
    NUMBERS.update({f"cuarenta y {UNITS[i]}": f"4{i}", f"treinta y {UNITS[i]}": f"3{i}",
                    f"veinte y {UNITS[i]}": f"2{i}", TEENS[i - 1]: f"1{i}", TWENTIES[i - 1]: f"2{i}"})
    NUMBERS.update({f"cuarenta {UNITS[i]}": f"4{i}", f"treinta {UNITS[i]}": f"3{i}",
                    f"veinte {UNITS[i]}": f"2{i}"})
    for q in range(1, 5):
        NUMBERS[f"{UNITS[q]} {UNITS[i]}"] = f"{q}{i}"


def result(understood=False, message="", proposal=None, incomplete=False, fragment="", selected=None, corrected=None, suggestions=None, context=None, draft=None):
    return {"understood": understood, "message": message, "proposal": proposal, "incomplete": incomplete,
            "fragment": fragment, "selectedPiece": selected, "correctedFrom": corrected,
            "suggestions": suggestions or [], "contextPiece": context, "draft": draft or proposal}


def unclear(message):
    return result(message=message)


def matches(text, aliases):
    return [key for key, pattern in aliases.items() if re.search(r"\b(?:" + pattern + r")\b", text)]


def clarification(message, piece, conditions=(), surfaces=(), note="", options=()):
    # Keep only slots understood without ambiguity. Never guess a clinical condition.
    fragment = " ".join(([f"pieza {piece}"] if piece else [])
                        + [WORDS[c] for c in conditions] + ["cara " + s.lower() for s in surfaces])
    if note:
        fragment += ". Observación: " + note
    return result(message=message, incomplete=bool(fragment), fragment=fragment, context=piece,
                  suggestions=[{"value": WORDS[c], "label": LABELS[c]} for c in options],
                  draft={"piece": piece, "condition": next(iter(conditions), None), "surface": next(iter(surfaces), "GENERAL"), "note": note} if fragment else None)


def similar_conditions(text):
    words = [w for w in text.split() if len(w) >= 4]
    scores = []
    for key, aliases in CONDITIONS.items():
        score = max((SequenceMatcher(None, word, alias).ratio() for word in words
                     for alias in aliases.split("|") if " " not in alias), default=0)
        if score >= 0.7:
            scores.append((score, key))
    best = max((score for score, _ in scores), default=0)
    return [key for score, key in sorted(scores, reverse=True)[:3] if score >= best - 0.12]


def parse(text: str, context_piece=None):
    if not isinstance(text, str) or not text.strip() or len(text) > 600:
        return unclear("Escribe o dicta una pieza y su condición.")
    parts = re.split(r"\b(?:observaci[oó]n(?:es)?|notas?)\s*:?\s*", text, maxsplit=1, flags=re.I)
    command = plain(parts[0])
    command = re.sub(r"\bmasilar\b", "maxilar", command)
    command = re.sub(r"\bcondicion\b", " ", command)
    # Conversational lead-ins do not change the finding; anatomical contradictions do.
    command = re.sub(r"\b(?:el diente|la pieza|el numero) (?:esta bien|es correcto|esta correcto|esta correcta)\b", "diente", command)
    command = re.sub(r"\b(?:ok|okay|bueno|bien|vamos a iniciar|vamos a empezar|he encontrado|hemos encontrado|se observa|se evidencia|observo|encontramos)\b", " ", command)
    command = re.sub(r"\s+", " ", command).strip()
    command = re.sub(r"\bempieza(?: a)?\b", "en pieza", command)
    command = re.sub(r"\bcar[ao]\s*oclusal\b", "cara oclusal", command)
    contextual = r"\b(?:ese|esa|este|esta|el mismo|la misma) (?:diente|pieza)(?: dental)?\b"
    if re.search(contextual, command):
        if context_piece not in TEETH:
            return unclear("¿A qué pieza te refieres? Selecciónala o indica su número FDI.")
        command = re.sub(contextual, f"pieza {context_piece}", command)
    note = parts[1].strip() if len(parts) > 1 else ""
    if len(note) > 300:
        return unclear("La observación puede tener hasta 300 caracteres.")
    for phrase, number in NUMBERS.items():
        command = re.sub(r"\b" + phrase + r"\b", number, command)
    for i in range(1, 9):
        for tens in (10, 20, 30, 40):
            command = re.sub(rf"\b{tens} y {UNITS[i]}\b", str(tens + i), command)
    command = re.sub(r"\b(10|20|30|40) y ([1-8])\b", lambda m: str(int(m[1]) + int(m[2])), command)
    command = re.sub(r"\b([1-4]) ([1-8])\b", r"\1\2", command)
    correction = re.fullmatch(r"no (?:no )?era (?:el )?(?:diente |pieza )?(\d{2}) era (?:el )?(?:diente |pieza )?(\d{2})", command)
    if correction and not note:
        old, new = map(int, correction.groups())
        if old in TEETH and new in TEETH:
            return result(True, f"Pieza corregida a {new}. Revisa antes de confirmar.", selected=new, corrected=old)
    numbers = re.findall(r"\b\d+\b", command)
    if len(numbers) > 1 or numbers and len(numbers[0]) != 2:
        return unclear("Escuché más de un número o un número incompleto. ¿Qué pieza FDI quisiste decir? Por ejemplo, «diente número treinta y seis».")
    piece = int(numbers[0]) if numbers else context_piece if context_piece in TEETH else None
    if piece is not None and piece not in TEETH:
        return unclear(f"Escuché pieza {piece}, pero no está en este odontograma. ¿Qué número quisiste decir? Usa 11–18, 21–28, 31–38 o 41–48.")
    if re.search(r"\b(?:no|sin|posible|sospecha|descartar|descarta|elimina|eliminar|borra|borrar|quita|quitar|necesita|requiere|pendiente|planificar|voy a|vamos a|hay que|debe|deberia)\b", command):
        return clarification("La frase contiene una negación, duda o tratamiento pendiente. Conservo la pieza; indica el hallazgo actual que quieres registrar.", piece, options=CONDITIONS)
    upper = bool(re.search(r"\b(?:maxilar|arcada) superior\b", command))
    lower = bool(re.search(r"\b(?:(?:maxilar|arcada) inferior|mandibula)\b", command))
    if upper and lower or piece and (upper and piece >= 30 or lower and piece < 30):
        return unclear(f"La arcada mencionada no coincide con la pieza {piece or 'indicada'}. ¿Qué pieza y arcada quisiste decir?")
    command = re.sub(r"\b(?:(?:maxilar|arcada) (?:superior|inferior)|mandibula)\b", " ", command)
    conditions, surfaces = matches(command, CONDITIONS), matches(command, SURFACES)
    if len(conditions) > 1:
        return clarification("Oí más de una condición. Elige una de las opciones o repite solo la condición para esta pieza.", piece, surfaces=surfaces if len(surfaces) == 1 else (), note=note, options=conditions)
    if len(surfaces) > 1:
        response = clarification("Conservo la pieza y la condición. ¿Qué cara quieres registrar primero?", piece, conditions, note=note)
        response["suggestions"] = [{"value": "cara " + s.lower(), "label": s.capitalize()} for s in surfaces]
        return response
    if piece is not None and not conditions and not surfaces and not note and re.fullmatch(
        rf"(?:por favor )?(?:selecciona|seleccionar|elige|elegir|muestra|mostrar|resalta|resaltar) (?:el |la )?(?:pieza dental |diente |pieza )?(?:numero )?{piece}(?: por favor)?", command):
        return result(True, f"Pieza {piece} seleccionada. Ya puedes completar la propuesta con condición, cara u observación.", selected=piece, context=piece,
                      draft={"piece": piece, "condition": None, "surface": "GENERAL", "note": ""})
    remainder = command
    for aliases in list(CONDITIONS.values()) + list(SURFACES.values()):
        remainder = re.sub(r"\b(?:" + "|".join(sorted(aliases.split("|"), key=len, reverse=True)) + r")\b", " ", remainder)
    remainder = re.sub(r"\b(?:\d+|pieza|dental|diente|numero|en|el|la|cara|superficie|con|un|una|de|del|al|a|coro|voz|corovoz|registrar|registra|marcar|marca|agregar|agrega|poner|pon|tiene|presenta|hay|por|favor|odontograma|ahora|esta|estaba|se|encuentra|quiero|que|es|como|digo|decir|lo|veo|tengo|observa|observamos|muela|dientes)\b", " ", remainder).strip()
    if remainder:
        options = similar_conditions(remainder) if not conditions else []
        return clarification(f"No entendí «{remainder[:60]}». "
                             + (f"Conservo la pieza {piece}. " if piece else "")
                             + ("¿Quisiste decir alguna de estas condiciones? Elige una o repite la condición." if options else "Aclara ese dato; puedes añadir detalles diciendo «observación».")
                             + " Puedes decir «repetir dictado» para escuchar lo anterior.",
                             piece, conditions, surfaces, note, options or ([] if conditions else CONDITIONS))
    if piece is None or not conditions:
        if piece is None and not conditions and not surfaces:
            return unclear("Indica la pieza y su condición.")
        message = "Entendí la condición. ¿Qué número de pieza FDI es? Puedes decir «diente número treinta y seis»." if piece is None else f"Ya inicié la propuesta para el diente {piece}. Puedes añadir condición, observación o cara en el orden que prefieras."
        return clarification(message, piece, conditions, surfaces, note, CONDITIONS if not conditions else ())
    if conditions[0] == "EXTRAIDO" and surfaces and surfaces[0] != "GENERAL":
        response = clarification("Extraído corresponde al diente completo. ¿Quieres indicar cara general o cambiar la condición?", piece, conditions, note=note)
        response["suggestions"] = [{"value": "cara general", "label": "Diente completo"}]
        return response
    if conditions[0] == "SANO" and note:
        return unclear("Marcar sano quitará los hallazgos de esa pieza o cara y no guardará una observación. Dicta el hallazgo sin nota si quieres limpiarla.")
    return result(True, "Revisa la propuesta antes de agregarla.",
                  {"piece": piece, "surface": surfaces[0] if surfaces else "GENERAL", "condition": conditions[0], "note": note}, context=piece)


def revise(text, field, draft):
    """Change exactly one slot of a pending proposal; never persist a clinical record."""
    if field not in {"piece", "condition", "note", "surface"} or not isinstance(draft, dict):
        return unclear("Elige diente, condición, observación o cara para editar.")
    if not isinstance(text, str) or not text.strip() or len(text) > 600:
        return unclear("Indica el nuevo valor del campo elegido.")
    candidate = {k: draft.get(k) for k in ("piece", "condition", "surface", "note")}
    if (candidate["piece"] is not None and candidate["piece"] not in TEETH
            or candidate["condition"] is not None and candidate["condition"] not in CONDITIONS
            or candidate["surface"] not in SURFACES or not isinstance(candidate["note"], str)
            or len(candidate["note"]) > 300):
        return unclear("La propuesta no es válida. Dicta nuevamente el hallazgo.")
    if field == "note":
        candidate["note"] = re.sub(r"^\s*(?:observaci[oó]n(?:es)?|notas?)\s*:?\s*", "", text.strip(), flags=re.I)
        if not candidate["note"]:
            return unclear("Di la nueva observación o usa «eliminar observación» para quitarla.")
        if len(candidate["note"]) > 300:
            return unclear("La observación puede tener hasta 300 caracteres. Di solo la observación nueva.")
    elif field == "piece":
        value = plain(text)
        for phrase, number in NUMBERS.items():
            value = re.sub(r"\b" + phrase + r"\b", number, value)
        value = re.sub(r"\b(?:el|la|diente|pieza|dental|numero|es)\b", " ", value).strip()
        if not re.fullmatch(r"\d{2}", value) or int(value) not in TEETH:
            return unclear("Di solo la nueva pieza FDI, por ejemplo «quince» o «pieza 15».")
        candidate["piece"] = int(value)
    else:
        # A temporary parsing anchor is never returned as a clinical finding.
        anchor_piece = candidate["piece"] or 11
        anchor_condition = candidate["condition"] or "CARIES"
        parsed = parse(f"pieza {anchor_piece} " + (WORDS[anchor_condition] + " " if field == "surface" else "") + re.sub(r"^condici[oó]n\s*:?\s*", "", text.strip(), flags=re.I))
        proposal = parsed.get("proposal")
        if not proposal:
            return parsed
        if proposal["piece"] != anchor_piece or proposal["note"] or field == "condition" and proposal["surface"] != "GENERAL" or field == "surface" and proposal["condition"] != anchor_condition:
            return unclear("Indica solo el campo elegido. Los demás datos de la propuesta se conservarán.")
        candidate[field] = proposal[field]
    if not candidate["piece"] or not candidate["condition"]:
        return clarification("Propuesta actualizada. Puedes añadir condición, cara u observación; indica también la pieza si falta.",
                             candidate["piece"], [candidate["condition"]] if candidate["condition"] else (),
                             [candidate["surface"]], candidate["note"])
    # Reuse all tooth/condition/surface safety checks, including healthy notes and extracted faces.
    return parse(f"pieza {candidate['piece']} {WORDS[candidate['condition']]} cara {candidate['surface'].lower()}"
                 + (". Observación: " + candidate["note"] if candidate["note"] else ""))
