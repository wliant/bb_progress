package com.bb.progress.headshape;

import com.bb.progress.headshape.HeadShapeDtos.HeadShapeEntryRequest;
import com.bb.progress.headshape.HeadShapeDtos.HeadShapeEntryResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/head-shape")
public class HeadShapeController {

    private final HeadShapeService service;

    public HeadShapeController(HeadShapeService service) {
        this.service = service;
    }

    @GetMapping
    public List<HeadShapeEntryResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HeadShapeEntryResponse save(@Valid @RequestBody HeadShapeEntryRequest request) {
        return service.save(request);
    }

    @PutMapping("/{id}")
    public HeadShapeEntryResponse update(@PathVariable UUID id,
            @Valid @RequestBody HeadShapeEntryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
