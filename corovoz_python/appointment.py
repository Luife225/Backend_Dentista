"""Prepare an appointment form, never create or reserve an appointment."""
import re
from datetime import datetime, timedelta, timezone, date
from knowledge import plain

ZONE = timezone(timedelta(hours=-5))
DAYS = ["lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"]
MONTHS = "enero febrero marzo abril mayo junio julio agosto septiembre octubre noviembre diciembre".split()
HOURS = ["cero", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once", "doce"]


def analyze_appointment(text, now=None):
    q = plain(text)
    if not re.search(r"\b(?:agend[a-z]*|program[a-z]*|reserv[a-z]*|cre[a-z]*)\b", q) or not re.search(r"\b(?:cita|consulta)\b", q):
        return None
    if re.search(r"\b(?:como|donde|cancelar|reprogramar)\b", q):
        return None
    today = (now or datetime.now(ZONE)).astimezone(ZONE).date()
    requested_date = None
    clarification = ""
    # Keep original numeric separators for dates and clock times.
    raw = text.lower()
    numeric = re.search(r"\b(\d{4})-(\d{2})-(\d{2})\b|\b(\d{1,2})/(\d{1,2})/(\d{4})\b", raw)
    calendar = re.search(r"\b(\d{1,2}) de (" + "|".join(MONTHS) + r")(?: de (\d{4}))?\b", q)
    try:
        if numeric:
            groups = numeric.groups()
            requested_date = date(int(groups[0]), int(groups[1]), int(groups[2])) if groups[0] else date(int(groups[5]), int(groups[4]), int(groups[3]))
        elif calendar:
            requested_date = date(int(calendar[3] or today.year), MONTHS.index(calendar[2]) + 1, int(calendar[1]))
            if not calendar[3] and requested_date < today:
                requested_date = requested_date.replace(year=today.year + 1)
        elif "pasado manana" in q:
            requested_date = today + timedelta(days=2)
        elif re.search(r"\bmanana\b", re.sub(r"de la manana", "", q)):
            requested_date = today + timedelta(days=1)
        elif re.search(r"\bhoy\b", q):
            requested_date = today
        else:
            days = [i for i, day in enumerate(DAYS) if re.search(r"\b" + day + r"\b", q)]
            if len(days) == 1:
                requested_date = today + timedelta(days=(days[0] - today.weekday()) % 7 or 7)
            elif len(days) > 1:
                clarification = "Mencionaste más de un día. Indica una sola fecha para la cita."
    except ValueError:
        clarification = "La fecha indicada no existe. Indica una fecha válida, por ejemplo 28/10/2026."
    if requested_date and requested_date < today:
        clarification = "La fecha indicada ya pasó. Indica una fecha futura."

    clock_text = raw
    for hour, word in enumerate(HOURS):
        clock_text = re.sub(r"\b" + word + r"\b", str(hour), clock_text)
    clock_text = re.sub(r"\buno\b", "1", clock_text)
    clock = list(re.finditer(r"\ba las?\s+(\d{1,2})(?::(\d{2})|\s+y\s+(media|cuarto|\d{1,2}))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|de la (?:mañana|tarde|noche))?", clock_text))
    requested_time = None
    if len(clock) > 1:
        clarification = "Mencionaste más de una hora. Indica una sola hora para la cita."
    elif clock:
        match = clock[0]
        hour = int(match[1])
        minutes = int(match[2] or ("30" if match[3] == "media" else "15" if match[3] == "cuarto" else match[3] or "0"))
        period = match[4] or ""
        if period and not 1 <= hour <= 12 or not period and not 0 <= hour <= 23 or minutes > 59:
            clarification = "La hora indicada no es válida. Indícala en formato de 24 horas."
        elif not period and 1 <= hour <= 12:
            clarification = f"¿A las {hour}:{minutes:02d} de la mañana o de la tarde? Indica la hora completa, por ejemplo «a las 15:00»."
        else:
            if period:
                hour = hour % 12 + (12 if re.search(r"p|tarde|noche", period) else 0)
            requested_time = f"{hour:02d}:{minutes:02d}"
    if not clarification and (requested_date is None or requested_time is None):
        clarification = "¿Para qué fecha y hora quieres la cita? Indica " + ("la fecha y la hora." if not requested_date and not requested_time else "la fecha." if not requested_date else "la hora, incluyendo mañana o tarde.")

    name = re.search(r"\b(?:paciente|para|a nombre de)\s+(?!hoy\b|manana\b|pasado\b|el\b|la\b|las\b|una?\b|\d)([a-z ]+)", q)
    search = []
    if name:
        part = re.split(r"\b(?:hoy|manana|pasado|a las?|el|con|por|para|motivo|modalidad|presencial|virtual|de la|" + "|".join(DAYS) + r")\b", name[1], maxsplit=1)[0]
        search = [t for t in part.split() if t not in {"paciente", "llamado", "llamada", "ella", "el", "esa", "ese", "misma", "mismo", "persona"}]
    reason = re.search(r"\b(?:motivo|por|para una?|para)\s*:?\s*(limpieza|control|evaluaci[oó]n|revisi[oó]n|extracci[oó]n|ortodoncia|endodoncia|restauraci[oó]n)([^.;]*)", raw)
    return {"handles": True, "restricted": False, "field": "appointment_draft", "fields": ["appointment_draft"],
            "search": search, "agenda": True,
            "appointment": {"date": requested_date.isoformat() if requested_date else None, "time": requested_time,
                            "reason": (reason[1] + reason[2]).strip()[:500] if reason else "",
                            "modality": "VIRTUAL" if re.search(r"\b(?:virtual|teleodontologia)\b", q) else "PRESENCIAL",
                            "clarification": clarification}}
