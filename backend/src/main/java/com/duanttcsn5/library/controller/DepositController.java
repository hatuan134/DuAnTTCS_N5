package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.loan.DepositDtos.*;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.DepositService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/deposits")
@PreAuthorize("hasAnyRole('ADMIN','LIBRARY_MANAGER','LIBRARIAN','READER')")
public class DepositController {
    private final DepositService service;
    public DepositController(DepositService service) {this.service=service;}
    @GetMapping("/policies") @PreAuthorize("hasAnyRole('ADMIN','LIBRARY_MANAGER','LIBRARIAN')")
    public Policies policies(@RequestParam(defaultValue="0") int page,@AuthenticationPrincipal UserPrincipal actor) {return service.policies(page,actor.id());}
    @PostMapping("/policies") @PreAuthorize("hasAnyRole('ADMIN','LIBRARY_MANAGER')")
    public Policies policy(@RequestBody PolicyRequest request,@AuthenticationPrincipal UserPrincipal actor) {return service.policy(request,actor.id());}
    @PostMapping("/prepare") @PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
    public List<Account> prepare(@RequestBody PrepareRequest request,@AuthenticationPrincipal UserPrincipal actor) {return service.prepare(request,actor.id());}
    @GetMapping
    public Accounts accounts(@RequestParam(required=false) Long readerId,@RequestParam(defaultValue="") String query,
            @RequestParam(defaultValue="0") int page,@AuthenticationPrincipal UserPrincipal actor) {return service.accounts(readerId,query,page,actor.id());}
    @GetMapping("/readers/{readerId}/transactions")
    public Transactions history(@PathVariable Long readerId,@RequestParam(defaultValue="0") int page,@AuthenticationPrincipal UserPrincipal actor) {return service.history(readerId,page,actor.id());}
    @PostMapping("/readers/{readerId}/{kind}") @PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
    public Transaction transact(@PathVariable Long readerId,@PathVariable String kind,@RequestBody TransactionRequest request,
            @AuthenticationPrincipal UserPrincipal actor) {return service.transact(readerId,kind,request,actor.id());}
}
