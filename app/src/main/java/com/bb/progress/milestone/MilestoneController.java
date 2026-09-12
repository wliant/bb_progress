package com.bb.progress.milestone;

import com.bb.progress.milestone.MilestoneDtos.AchievementRequest;
import com.bb.progress.milestone.MilestoneDtos.AchievementView;
import com.bb.progress.milestone.MilestoneDtos.AgeGroupView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/milestones")
public class MilestoneController {

    private final MilestoneService service;

    public MilestoneController(MilestoneService service) {
        this.service = service;
    }

    @GetMapping
    public List<AgeGroupView> list() {
        return service.listGroupedByAge();
    }

    @PutMapping("/{definitionId}/achievement")
    public AchievementView setAchievement(@PathVariable String definitionId,
            @Valid @RequestBody AchievementRequest request) {
        return service.setAchievement(definitionId, request);
    }

    @DeleteMapping("/{definitionId}/achievement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAchievement(@PathVariable String definitionId) {
        service.removeAchievement(definitionId);
    }


}
