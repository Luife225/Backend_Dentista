"""Interpret record queries locally. Only Java reads data and enforces clinical access."""

import re

from knowledge import plain
from appointment import analyze_appointment

FILLERS = set("a al con cual cuales cuando de del el en es esta la las lo los me mi para por que quiero su sus tiene un una y sera seria fue proxima proximo siguiente ultima ultimo cita citas consulta consultas paciente telefono telefonico celular numero correo email contacto estado activo activa informacion general datos dato resumen administrativo administrativa dame dime puedes saber necesito ver buscar busca consultar muestra muestrame favor corovoz dni documento identidad llama llamado llamada persona ese esa este seleccionado acerca sobre quisiera podrias dar darme mostrar obtener hora fecha dia viene venir vuelve volver regresa regresar retorna retornar otra vez le toca programada programado pendiente encuentra encontrar localiza localizar ubica ubicar senor senora llamarse nombre ficha entrar entro revisa revisar se".split())


def has(text, alternatives):
    return bool(re.search(r"\b(?:" + alternatives + r")\b", text))


def administrative_analyze(text):
    q = plain(text)
    handles = True
    if not has(q, "paciente") and has(q, "odontograma|version|versiones|historia|clinica|consultorio|usuario|usuarios|cuenta|cuentas"):
        handles = False
    elif has(q, "como|donde|ejemplo|ejemplos|puedo|puedes") and not has(q, "dame|dime|muestra|muestrame|sera|telefono|correo|proxima|siguiente|ultima"):
        handles = False
    elif has(q, "crear|registrar|registro|editar|edito|cambiar|cambio|actualizar|modificar|agendar|agenda|reprogramar|cancelar|activar|desactivar|recuperar|vincular"):
        handles = False
    elif has(q, "usuarios|cuentas|coronyx|corovoz") and not has(q, "paciente|cita|telefono|correo"):
        handles = False
    elif not (
        has(q, "telefono|celular|correo|email|contacto|resumen")
        or has(q, "proxima|proximo|siguiente|ultima|ultimo") and has(q, "cita|consulta")
        or has(q, "cuando") and has(q, "cita|consulta")
        or has(q, "dia|fecha|hora") and has(q, "cita|consulta")
        or has(q, "dia|fecha|hora|cuando") and has(q, "programado|programada") and has(q, "paciente")
        or has(q, "cuando|proxima|proximo|siguiente|pendiente") and has(q, "viene|volver|vuelve|regresa|regresar|retorna|retornar|toca")
        or has(q, "buscar|busca|encuentra|encontrar|localiza|localizar|ubica|ubicar") and has(q, r"paciente|dni|documento|\ba \w+")
        or has(q, "su") and has(q, "estado")
        or has(q, "paciente") and has(q, "datos|informacion|estado|activo|activa|alergias|antecedentes|medicamentos|diagnostico")
    ):
        handles = False
    if has(q, "telefono|celular"):
        field = "phone"
    elif has(q, "correo|email"):
        field = "email"
    elif has(q, "estado|activo|activa"):
        field = "status"
    elif has(q, "contacto"):
        field = "contact"
    elif has(q, "ultima|ultimo"):
        field = "last_appointment"
    elif has(q, "cita|consulta|cuando|dia|fecha|hora|programado|programada|proxima|siguiente|viene|vuelve|volver|regresa|regresar|retorna|retornar|toca"):
        field = "next_appointment"
    else:
        field = "summary"
    agenda = has(q, "cita|consulta|cuando|dia|fecha|hora|programado|programada|proxima|siguiente|ultima|viene|vuelve|volver|regresa|regresar|retorna|retornar|toca")
    return {"handles": handles, "restricted": has(q, "alergias|antecedentes|medicamentos|diagnostico|historia|tratamiento"),
            "search": [term for term in q.split() if term not in FILLERS and len(term) > 1], "field": field, "agenda": agenda}


FIELDS = {
    "allergies": r"alergias?|alergic[oa]",
    "background": r"antecedentes(?: medicos)?",
    "medications": r"medicamentos?|medicacion",
    "radiographs": r"radiografias?|rayos x|panoramicas?",
    "documents": r"documentos?|archivos?|adjuntos?",
    "history": r"historia(?: clinica)?|historial(?: clinico)?|expediente|atenciones(?: clinicas)?",
    "diagnosis": r"diagnosticos?",
    "treatment": r"plan de tratamiento|tratamientos?",
    "prescriptions": r"recetas?|prescripciones?",
    "procedures": r"procedimientos?",
    "evolution": r"evolucion",
    "indications": r"indicaciones?",
    "examination": r"examen clinico",
    "vitals": r"signos vitales",
    "odontogram": r"odontogramas?",
    "blood_type": r"tipo de sangre|grupo sanguineo",
    "insurance": r"seguro",
    "address": r"direccion|domicilio|ciudad",
    "birth_date": r"fecha de nacimiento|cumpleanos|edad",
}
CLINICAL_FIELDS = set(FIELDS) - {"documents", "insurance", "address", "birth_date"}
FILLERS.update("ok okay bueno bien entonces pues eh este eso esto si no gracias disculpa disculpame momento espera esperame veras hablando dicho dije queria preguntarte saber saberlo preguntarle pregunta preguntando preguntar preguntaba preguntado revisando saberlo comentaba comenta comente cierto antes primero luego ahora despues tambien ademas aqui ahi aun ya mas solo realmente efectivamente alguna algun algunas algunos ninguno ninguna registrado registrada registrados registradas presenta presentan cuenta contiene tiene tenia tener existe existen hay aparecen aparece revisemos confirmame comprobar verificar detalle detalles completo completa exactamente porfa podrias podria puede puedes revisar confirmas confirma confirmame verificar indicame dime intentar intentando respecto vuelto retomando retomemos perdon perdona viene hoy ayer entender entendido entendi tal favor oye hola soy estoy estaba hemos hemos estado conversando hablado hablado".split())
FILLERS.update("ella ellos ellas esa misma mismo dicha dicho seleccionada mencionado mencionada anteriormente cuentame hablame".split())


def analyze(text):
    appointment = analyze_appointment(text)
    if appointment:
        return appointment
    q = plain(text)
    result = administrative_analyze(text)
    if re.fullmatch(r"(?:(?:y|de|sobre|hablame de|cuentame de) )?(?:el|ella|ese paciente|esa paciente|esa persona|el mismo|la misma)", q):
        result.update(handles=True, field="summary")
    fields = [field for field, pattern in FIELDS.items() if has(q, pattern)]
    # A document number is an identifier, not a request to list attachments.
    if has(q, r"numero (?:de )?documento|dni|cedula"):
        fields = [field for field in fields if field != "documents"]
    instruction = has(q, "como|donde") and not has(q, "tiene|presenta|registrad[oa]s?|su|sus")
    mutation = has(q, "crear|registrar|editar|cambiar|actualizar|modificar|agendar|reprogramar|cancelar|subir|cargar|eliminar|borrar")
    advice = has(q, "recomienda|recomiendas|recetar|receto|dar|tomo|tomar|deberia|debo|necesita|necesitaria")
    if fields and not instruction and not mutation and not advice:
        result.update(handles=True, field=fields[0], fields=fields, agenda=False,
                      restricted=bool(CLINICAL_FIELDS.intersection(fields)))
    else:
        result["fields"] = [result["field"]]
    if instruction or mutation:
        result["handles"] = False
    search_text = q
    for pattern in FIELDS.values():
        search_text = re.sub(r"\b(?:" + pattern + r")\b", " ", search_text)
    # Remove common interjections without dropping an actual name or surname.
    search_text = re.sub(r"\b(?:es decir|a ver|por cierto|un momento|vamos a ver|lo que quiero|te queria preguntar)\b", " ", search_text)
    result["search"] = list(dict.fromkeys(term for term in search_text.split() if term not in FILLERS and len(term) > 1))
    return result
