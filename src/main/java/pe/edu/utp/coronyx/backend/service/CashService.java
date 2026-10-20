package pe.edu.utp.coronyx.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.utp.coronyx.backend.dto.BusinessDtos.*;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.security.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class CashService {
    private final CashChargeRepository charges;
    private final CashPaymentRepository payments;
    private final PatientRepository patients;
    private final AppointmentRepository appointments;
    public CashService(CashChargeRepository charges, CashPaymentRepository payments, PatientRepository patients, AppointmentRepository appointments) {
        this.charges=charges; this.payments=payments; this.patients=patients; this.appointments=appointments;
    }
    private UserPrincipal principal() {
        UserPrincipal p=SecurityUtils.getCurrentPrincipal();
        if (p.getClinicaId()==null || p.getMembership()==null || !Set.of("ADMIN_CLINICA","RECEPCIONISTA","ODONTOLOGO").contains(p.getRol()))
            throw new AccessDeniedException("Caja está reservada al personal de la clínica");
        return p;
    }
    private void requireAdmin(UserPrincipal p) {
        if (!"ADMIN_CLINICA".equals(p.getRol())) throw new AccessDeniedException("Solo el administrador puede anular operaciones de caja");
    }
    private CashCharge charge(Long id, UserPrincipal p) {
        return charges.lockById(id,p.getClinicaId()).orElseThrow(()->new NoSuchElementException("Cargo no encontrado"));
    }
    @Transactional(readOnly=true)
    public List<ChargeResponse> list() {
        UserPrincipal p=principal();
        return charges.findByClinicaIdOrderByFechaCreacionDesc(p.getClinicaId()).stream().map(this::response).toList();
    }
    public ChargeResponse create(ChargeRequest dto) {
        UserPrincipal p=principal();
        // El bloqueo del paciente serializa también reintentos de creación para ese paciente.
        Patient patient=patients.findLockedById(dto.pacienteId()).orElseThrow(()->new NoSuchElementException("Paciente no encontrado"));
        if (!patient.getClinica().getId().equals(p.getClinicaId())) throw new NoSuchElementException("Paciente no encontrado");
        CashCharge previous=charges.findByClinicaIdAndSolicitudId(p.getClinicaId(),dto.solicitudId()).orElse(null);
        if (previous!=null) {
            if (!previous.paciente.getId().equals(dto.pacienteId()) || previous.importe.compareTo(dto.importe())!=0
                    || !previous.concepto.equals(dto.concepto().trim()) || !Objects.equals(previous.cita==null?null:previous.cita.getId(),dto.citaId()))
                throw new IllegalStateException("La solicitud ya se utilizó para otro cargo");
            return response(previous);
        }
        Appointment appointment=null;
        if (dto.citaId()!=null) {
            appointment=appointments.findByIdAndClinicaId(dto.citaId(),p.getClinicaId()).orElseThrow(()->new NoSuchElementException("Cita no encontrada"));
            if (!appointment.getPaciente().getId().equals(patient.getId())) throw new IllegalArgumentException("La cita no corresponde al paciente");
        }
        requireAmount(dto.importe());
        CashCharge c=new CashCharge(); c.clinica=p.getClinic(); c.paciente=patient; c.cita=appointment;
        c.creadoPor=p.getMembership(); c.concepto=dto.concepto().trim(); c.importe=dto.importe();
        c.estado="ACTIVO"; c.solicitudId=dto.solicitudId(); c.fechaCreacion=OffsetDateTime.now();
        return response(charges.saveAndFlush(c));
    }
    public ChargeResponse pay(Long id, PaymentRequest dto) {
        UserPrincipal p=principal(); CashCharge c=charge(id,p);
        CashPayment previous=payments.findByClinicaIdAndSolicitudId(p.getClinicaId(),dto.solicitudId()).orElse(null);
        if (previous!=null) {
            if (!previous.cargo.getId().equals(id) || previous.importe.compareTo(dto.importe())!=0 || !previous.medio.equals(dto.medio())
                    || !Objects.equals(previous.referencia,dto.referencia())) throw new IllegalStateException("La solicitud ya se utilizó para otro pago");
            return response(c);
        }
        if (!"ACTIVO".equals(c.estado)) throw new IllegalStateException("El cargo está anulado");
        requireAmount(dto.importe());
        if (!Set.of("EFECTIVO","TARJETA","TRANSFERENCIA","YAPE","PLIN").contains(dto.medio())) throw new IllegalArgumentException("Medio de pago inválido");
        if (dto.importe().compareTo(c.importe.subtract(paid(c.id)))>0) throw new IllegalStateException("El pago supera el saldo pendiente");
        CashPayment payment=new CashPayment(); payment.clinica=p.getClinic(); payment.cargo=c;
        payment.creadoPor=p.getMembership(); payment.importe=dto.importe(); payment.medio=dto.medio();
        payment.referencia=dto.referencia(); payment.solicitudId=dto.solicitudId(); payment.fechaCreacion=OffsetDateTime.now();
        payments.saveAndFlush(payment);
        return response(c);
    }
    public ChargeResponse voidPayment(Long id, VoidRequest dto) {
        UserPrincipal p=principal(); requireAdmin(p);
        Long chargeId=payments.findChargeId(id,p.getClinicaId()).orElseThrow(()->new NoSuchElementException("Pago no encontrado"));
        CashCharge c=charge(chargeId,p);
        CashPayment payment=payments.findByIdAndClinicaId(id,p.getClinicaId()).orElseThrow();
        if (!payment.anulado) {
            payment.anulado=true; payment.motivoAnulacion=dto.motivo().trim();
            payment.anuladoPor=p.getMembership(); payment.fechaAnulacion=OffsetDateTime.now(); payments.saveAndFlush(payment);
        }
        return response(c);
    }
    public ChargeResponse voidCharge(Long id, VoidRequest dto) {
        UserPrincipal p=principal(); requireAdmin(p); CashCharge c=charge(id,p);
        if (paid(c.id).signum()>0) throw new IllegalStateException("Anule los pagos del cargo antes de anularlo");
        if (!"ANULADO".equals(c.estado)) { c.estado="ANULADO"; c.motivoAnulacion=dto.motivo().trim(); c.anuladoPor=p.getMembership(); c.fechaAnulacion=OffsetDateTime.now(); charges.save(c); }
        return response(c);
    }
    private void requireAmount(BigDecimal amount) {
        if (amount==null || amount.signum()<=0 || amount.scale()>2) throw new IllegalArgumentException("El importe debe ser positivo y tener hasta dos decimales");
    }
    private BigDecimal paid(Long id) {
        return payments.findByCargoIdOrderByFechaCreacionAsc(id).stream().filter(v->!v.anulado).map(v->v.importe).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private ChargeResponse response(CashCharge c) {
        List<CashPayment> list=payments.findByCargoIdOrderByFechaCreacionAsc(c.id);
        BigDecimal paid=list.stream().filter(v->!v.anulado).map(v->v.importe).reduce(BigDecimal.ZERO,BigDecimal::add);
        String state="ANULADO".equals(c.estado)?"ANULADO":paid.compareTo(c.importe)==0?"PAGADO":paid.signum()>0?"PARCIAL":"PENDIENTE";
        return new ChargeResponse(c.id.toString(),c.paciente.getId().toString(),c.paciente.getNombres()+" "+c.paciente.getApellidos(),
                c.cita==null?null:c.cita.getId().toString(),c.concepto,c.importe,paid,"ANULADO".equals(c.estado)?BigDecimal.ZERO:c.importe.subtract(paid),state,"PEN",
                c.motivoAnulacion,c.fechaCreacion,list.stream().map(v->new PaymentResponse(v.id.toString(),c.id.toString(),v.importe,v.medio,v.referencia,
                        v.anulado,v.motivoAnulacion,v.creadoPor.getId().toString(),v.fechaCreacion)).toList());
    }
}
