package pe.edu.utp.coronyx.backend.controller;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.BusinessDtos.*;
import pe.edu.utp.coronyx.backend.service.CashService;
import java.util.List;

@RestController
@RequestMapping("/api/v1/caja")
public class CashController {
    private final CashService service;
    public CashController(CashService service) { this.service=service; }
    @GetMapping("/cargos") public List<ChargeResponse> list() { return service.list(); }
    @PostMapping("/cargos") public ChargeResponse create(@Valid @RequestBody ChargeRequest dto) { return service.create(dto); }
    @PostMapping("/cargos/{id}/pagos") public ChargeResponse pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest dto) { return service.pay(id,dto); }
    @PostMapping("/cargos/{id}/anular") public ChargeResponse voidCharge(@PathVariable Long id, @Valid @RequestBody VoidRequest dto) { return service.voidCharge(id,dto); }
    @PostMapping("/pagos/{id}/anular") public ChargeResponse voidPayment(@PathVariable Long id, @Valid @RequestBody VoidRequest dto) { return service.voidPayment(id,dto); }
}
