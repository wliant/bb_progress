package com.bb.progress.growth;

import com.bb.progress.common.Gender;
import com.bb.progress.growth.GrowthDtos.GrowthRecordRequest;
import com.bb.progress.growth.GrowthDtos.GrowthRecordResponse;
import com.bb.progress.growth.WhoPercentileService.StandardsResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api")
public class GrowthController {

    private final GrowthService service;
    private final WhoPercentileService whoPercentileService;

    public GrowthController(GrowthService service, WhoPercentileService whoPercentileService) {
        this.service = service;
        this.whoPercentileService = whoPercentileService;
    }

    @GetMapping("/growth-records")
    public List<GrowthRecordResponse> list() {
        return service.findAll().stream().map(GrowthRecordResponse::from).toList();
    }

    @PostMapping("/growth-records")
    @ResponseStatus(HttpStatus.CREATED)
    public GrowthRecordResponse create(@Valid @RequestBody GrowthRecordRequest request) {
        return GrowthRecordResponse.from(service.create(request));
    }

    @PutMapping("/growth-records/{id}")
    public GrowthRecordResponse update(@PathVariable UUID id, @Valid @RequestBody GrowthRecordRequest request) {
        return GrowthRecordResponse.from(service.update(id, request));
    }

    @DeleteMapping("/growth-records/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping("/growth-standards")
    public StandardsResponse standards(@RequestParam Gender gender, @RequestParam GrowthMeasure measure) {
        return whoPercentileService.standards(measure, gender);
    }
}
