package com.example.busbooking.api.controller;

import com.example.busbooking.api.dto.operator.*;
import com.example.busbooking.api.mapper.OperatorDtoMapper;
import com.example.busbooking.business.service.OperatorService;
import com.example.busbooking.domain.enums.OperatorStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/operators")
@SecurityRequirement(name = "bearerAuth")
public class AdminOperatorController {
    private final OperatorService service;
    private final OperatorDtoMapper mapper;

    public AdminOperatorController(OperatorService service, OperatorDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List bus operators with optional text and status filters")
    public List<OperatorResponse> list(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) OperatorStatus status) {
        return service.search(q, status).stream().map(mapper::toResponse).toList();
    }

    @PostMapping
    @Operation(summary = "Create an active bus operator")
    public ResponseEntity<OperatorResponse> create(@Valid @RequestBody CreateOperatorRequest request) {
        OperatorResponse response = mapper.toResponse(service.create(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/admin/operators/" + response.id())).body(response);
    }

    @PutMapping("/{operatorId}")
    @Operation(summary = "Replace a bus operator")
    public OperatorResponse update(@PathVariable Long operatorId,
                                   @Valid @RequestBody OperatorRequest request) {
        return mapper.toResponse(service.update(operatorId, request.toCommand()));
    }
}
