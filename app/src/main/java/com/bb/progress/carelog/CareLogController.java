package com.bb.progress.carelog;

import com.bb.progress.carelog.CareLogDtos.CareLogCreateRequest;
import com.bb.progress.carelog.CareLogDtos.CareLogResponse;
import com.bb.progress.carelog.CareLogDtos.CareLogUpdateRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/care-logs")
public class CareLogController {

    private final CareLogService service;

    public CareLogController(CareLogService service) {
        this.service = service;
    }

    @GetMapping
    public List<CareLogResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) CareType type) {
        return service.findByDay(date, type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CareLogResponse create(@Valid @RequestBody CareLogCreateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public CareLogResponse update(@PathVariable UUID id, @Valid @RequestBody CareLogUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }



}
