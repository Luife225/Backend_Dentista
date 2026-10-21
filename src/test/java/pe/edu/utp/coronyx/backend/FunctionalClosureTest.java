package pe.edu.utp.coronyx.backend;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.utp.coronyx.backend.model.*;
import pe.edu.utp.coronyx.backend.repository.*;
import pe.edu.utp.coronyx.backend.service.*;
import pe.edu.utp.coronyx.backend.security.*;
import pe.edu.utp.coronyx.backend.dto.*;
import pe.edu.utp.coronyx.backend.dto.BusinessDtos.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

class FunctionalClosureTest {
    Clinic clinic; User user; ClinicUser doctor; Patient patient; UserPrincipal principal;
    @BeforeEach void identity() {
        clinic=new Clinic(); clinic.setId(1L);
        user=new User("test@example.invalid","hash","Prueba","Usuario"); user.setId(2L);
        doctor=new ClinicUser(); doctor.setId(3L); doctor.setClinica(clinic); doctor.setUsuario(user);
        patient=new Patient(); patient.setId(4L); patient.setClinica(clinic); patient.setNombres("Paciente"); patient.setApellidos("Prueba");
        login("ODONTOLOGO");
    }
    void login(String role) {
        principal=new UserPrincipal(user,doctor,clinic,patient,role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    Appointment appointment(String state) {
        Appointment a=new Appointment(); a.setId(5L); a.setClinica(clinic); a.setPaciente(patient); a.setOdontologo(doctor); a.setEstado(state); return a;
    }
    @Test void terminalAppointmentsCannotReopen() {
        for (String state:List.of("CANCELADA","FINALIZADA","NO_ASISTIO")) {
            assertThrows(IllegalStateException.class,()->AppointmentTransitions.requireTransition(state,"EN_ATENCION"));
            assertThrows(IllegalStateException.class,()->AppointmentTransitions.requireReschedulable(state));
        }
    }
    @Test void receptionAttendanceDoesNotGrantClinicalFinalization() {
        assertThrows(IllegalStateException.class,()->AppointmentTransitions.requireTransition("CONFIRMADA","FINALIZADA"));
        assertDoesNotThrow(()->AppointmentTransitions.requireTransition("EN_ATENCION","FINALIZADA"));
    }
    @Test void clinicalIdsRemainExactStrings() {
        var dto=new ClinicalEncounterResponseDto(); dto.setId(9007199254740993L); dto.setCitaId(9007199254740995L);
        assertEquals("9007199254740993",dto.getId()); assertEquals("9007199254740995",dto.getCitaId());
        var version=new ClinicalVersionResponseDto(); version.setId(9007199254740997L);
        assertEquals("9007199254740997",version.getId());
    }
    @Test void disguisedHtmlCannotUploadAsPdf() {
        assertThrows(IllegalArgumentException.class,()->FileContentValidator.detect(new MockMultipartFile("file","image.pdf","application/pdf","<script>alert(1)</script>".getBytes())));
        assertEquals("application/pdf",FileContentValidator.detect(new MockMultipartFile("file","image.pdf","text/plain","%PDF-1.4\nvalid header".getBytes())));
    }
    @Test void meetingLinksRejectOtherOriginsAndNewRoomFallback() {
        assertEquals("https://meet.google.com/abc-defg-hij",TeleconsultationLinks.validate("https://meet.google.com/abc-defg-hij"));
        assertNotNull(TeleconsultationLinks.validate("https://us02web.zoom.us/j/123456789?pwd=abc"));
        for (String bad:List.of("javascript:alert(1)","https://meet.google.com/new","https://meet.google.com.evil.test/abc-defg-hij","http://meet.google.com/abc-defg-hij"))
            assertThrows(IllegalArgumentException.class,()->TeleconsultationLinks.validate(bad));
    }
    @Test void patientCannotDeleteDoctorAbsence() {
        login("PACIENTE");
        var absences=mock(AusenciaOdontologoRepository.class); var a=new AusenciaOdontologo(); a.setClinica(clinic); a.setOdontologo(doctor);
        when(absences.findById(10L)).thenReturn(Optional.of(a));
        var service=new AvailabilityService(mock(HorarioAtencionRepository.class),absences,mock(ClinicRepository.class),mock(ClinicUserRepository.class),mock(AppointmentRepository.class));
        assertThrows(AccessDeniedException.class,()->service.deleteDoctorAbsence(10L,principal)); verify(absences,never()).delete(any());
    }
    @Test void administratorCannotWriteScheduleInAnotherClinic() {
        login("ADMIN_CLINICA"); var clinics=mock(ClinicRepository.class);
        var service=new AvailabilityService(mock(HorarioAtencionRepository.class),mock(AusenciaOdontologoRepository.class),clinics,mock(ClinicUserRepository.class),mock(AppointmentRepository.class));
        assertThrows(AccessDeniedException.class,()->service.updateClinicSchedule(999L,List.of(),principal)); verifyNoInteractions(clinics);
    }
    @Test void nonDentistsCannotReadTechnicalOdontogram() {
        var service=new OdontogramService(mock(VersionOdontogramaRepository.class),mock(HallazgoOdontogramaRepository.class),mock(PatientRepository.class));
        for (String role:List.of("ADMIN_CLINICA","RECEPCIONISTA","PACIENTE")) {
            login(role); assertThrows(AccessDeniedException.class,()->service.getCurrentOdontogram(4L,principal));
        }
    }
    @Test void startAppointmentReturnsExistingDraftWithoutDuplicating() {
        var appts=mock(AppointmentRepository.class); var encounters=mock(AtencionClinicaRepository.class);
        Appointment a=appointment("EN_ATENCION"); when(appts.findLockedByIdAndClinicaId(5L,1L)).thenReturn(Optional.of(a));
        AtencionClinica e=new AtencionClinica(); e.setId(6L); e.setClinica(clinic); e.setPaciente(patient); e.setOdontologo(doctor); e.setCita(a); e.setEstado("BORRADOR");
        when(encounters.findByCitaId(5L)).thenReturn(Optional.of(e));
        var versions=mock(VersionAtencionClinicaRepository.class);
        var service=new ClinicalAttentionService(encounters,versions,mock(PatientRepository.class),appts);
        assertEquals("6",service.startAppointment(5L).getId()); verify(encounters,never()).save(any());
    }
    @Test void anotherDentistCannotStartAppointment() {
        var appts=mock(AppointmentRepository.class); Appointment a=appointment("CONFIRMADA");
        var another=new ClinicUser(); another.setId(99L); a.setOdontologo(another);
        when(appts.findLockedByIdAndClinicaId(5L,1L)).thenReturn(Optional.of(a));
        var service=new ClinicalAttentionService(mock(AtencionClinicaRepository.class),mock(VersionAtencionClinicaRepository.class),mock(PatientRepository.class),appts);
        assertThrows(AccessDeniedException.class,()->service.startAppointment(5L));
    }
    @Test void cancelledAppointmentCannotBeSigned() {
        var appts=mock(AppointmentRepository.class); var encounters=mock(AtencionClinicaRepository.class); var versions=mock(VersionAtencionClinicaRepository.class);
        Appointment a=appointment("CANCELADA"); when(appts.findLockedByIdAndClinicaId(5L,1L)).thenReturn(Optional.of(a));
        AtencionClinica e=new AtencionClinica(); e.setId(6L); e.setClinica(clinic); e.setPaciente(patient); e.setOdontologo(doctor); e.setCita(a); e.setEstado("BORRADOR");
        VersionAtencionClinica v=new VersionAtencionClinica(); v.setId(7L); v.setAtencionClinica(e); v.setNumeroVersion(1); v.setMotivoConsulta("Control"); v.setDiagnostico("Diagnóstico registrado");
        when(encounters.findLockedByIdAndClinicaId(6L,1L)).thenReturn(Optional.of(e)); when(versions.findByIdAndClinicaId(7L,1L)).thenReturn(Optional.of(v));
        when(versions.findTopByAtencionClinicaIdOrderByNumeroVersionDesc(6L)).thenReturn(Optional.of(v));
        var service=new ClinicalAttentionService(encounters,versions,mock(PatientRepository.class),appts);
        ConfirmVersionDto confirmation=new ConfirmVersionDto(7L,null); confirmation.setRevision(0L);
        assertThrows(IllegalStateException.class,()->service.confirmVersion(6L,confirmation));
        assertEquals("CANCELADA",a.getEstado()); assertFalse(Boolean.TRUE.equals(v.getEstaConfirmado())); verify(versions,never()).save(any());
    }
    @Test void inventoryCannotBecomeNegativeAndFailedMoveHasNoLedgerEntry() {
        login("RECEPCIONISTA"); var items=mock(InventoryItemRepository.class); var moves=mock(InventoryMovementRepository.class);
        InventoryItem i=new InventoryItem(); i.id=9L; i.stock=2; i.activo=true;
        when(items.lockById(9L,1L)).thenReturn(Optional.of(i));
        var service=new InventoryService(items,moves);
        assertThrows(IllegalStateException.class,()->service.move(9L,new MovementRequest("SALIDA",3,"Uso","req-1")));
        assertEquals(2,i.stock); verify(moves,never()).saveAndFlush(any());
    }
    @Test void repeatedInventoryMoveDoesNotSubtractTwice() {
        login("RECEPCIONISTA"); var items=mock(InventoryItemRepository.class); var moves=mock(InventoryMovementRepository.class);
        InventoryItem i=new InventoryItem(); i.id=9L; i.stock=2; i.activo=true;
        InventoryMovement m=new InventoryMovement(); m.insumo=i; m.tipo="SALIDA"; m.cantidad=3; m.motivo="Uso";
        when(items.lockById(9L,1L)).thenReturn(Optional.of(i)); when(moves.findByClinicaIdAndSolicitudId(1L,"req-1")).thenReturn(Optional.of(m));
        assertEquals(2,new InventoryService(items,moves).move(9L,new MovementRequest("SALIDA",3,"Uso","req-1")).stock());
        verify(moves,never()).saveAndFlush(any());
    }
    @Test void receptionCannotAdjustStock() {
        login("RECEPCIONISTA"); var items=mock(InventoryItemRepository.class); InventoryItem i=new InventoryItem(); i.id=9L; i.activo=true;
        when(items.lockById(9L,1L)).thenReturn(Optional.of(i));
        assertThrows(AccessDeniedException.class,()->new InventoryService(items,mock(InventoryMovementRepository.class)).move(9L,new MovementRequest("AJUSTE",10,"Conteo","req-1")));
    }
    @Test void patientCannotListBusinessData() {
        login("PACIENTE");
        assertThrows(AccessDeniedException.class,()->new InventoryService(mock(InventoryItemRepository.class),mock(InventoryMovementRepository.class)).list());
        assertThrows(AccessDeniedException.class,()->new CashService(mock(CashChargeRepository.class),mock(CashPaymentRepository.class),mock(PatientRepository.class),mock(AppointmentRepository.class)).list());
    }
    @Test void paymentCannotExceedRemainingBalance() {
        login("RECEPCIONISTA"); var charges=mock(CashChargeRepository.class); var payments=mock(CashPaymentRepository.class);
        CashCharge c=new CashCharge(); c.id=9L; c.estado="ACTIVO"; c.importe=new BigDecimal("100.00");
        CashPayment paid=new CashPayment(); paid.importe=new BigDecimal("80.00");
        when(charges.lockById(9L,1L)).thenReturn(Optional.of(c)); when(payments.findByCargoIdOrderByFechaCreacionAsc(9L)).thenReturn(List.of(paid));
        assertThrows(IllegalStateException.class,()->new CashService(charges,payments,mock(PatientRepository.class),mock(AppointmentRepository.class)).pay(9L,new PaymentRequest(new BigDecimal("20.01"),"EFECTIVO",null,"req-1")));
        verify(payments,never()).saveAndFlush(any());
    }
    @Test void cancelledChargeCannotReceivePayments() {
        login("RECEPCIONISTA"); var charges=mock(CashChargeRepository.class); var payments=mock(CashPaymentRepository.class);
        CashCharge c=new CashCharge(); c.id=9L; c.estado="ANULADO";
        when(charges.lockById(9L,1L)).thenReturn(Optional.of(c));
        assertThrows(IllegalStateException.class,()->new CashService(charges,payments,mock(PatientRepository.class),mock(AppointmentRepository.class)).pay(9L,new PaymentRequest(BigDecimal.ONE,"EFECTIVO",null,"req-1")));
        verify(payments,never()).saveAndFlush(any());
    }
}
