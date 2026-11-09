package com.bb.progress.carelog;

import com.bb.progress.carelog.CareAggregateDtos.CareAggregateResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view over the care log, owning no data of its own — the same arrangement as
 * {@code /api/media}. Entries are added and edited through {@code /api/care-logs}.
 */
@RestController
@RequestMapping("/api/care-aggregates")
public class CareAggregateController {

    private final CareAggregateService service;

    public CareAggregateController(CareAggregateService service) {
        this.service = service;
    }

    @GetMapping
    public CareAggregateResponse aggregate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.aggregate(from, to);
    }
}
