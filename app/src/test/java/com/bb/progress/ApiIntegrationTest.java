package com.bb.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full-stack integration tests: real Spring context against a Testcontainers
 * PostgreSQL with Flyway migrations and seed data applied.
 */
@Tag("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    @org.springframework.test.context.DynamicPropertySource
    static void photoDir(org.springframework.test.context.DynamicPropertyRegistry registry) throws Exception {
        Path dir = Files.createTempDirectory("bb-progress-photos");
        registry.add("app.photo-dir", dir::toString);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void babyProfileLifecycle() throws Exception {
        mockMvc.perform(get("/api/baby"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BABY_NOT_FOUND"));

        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小宝\",\"dateOfBirth\":\"2026-01-15\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("小宝"))
                .andExpect(jsonPath("$.hasPhoto").value(false));

        // Upsert keeps a single row.
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小宝贝\",\"dateOfBirth\":\"2026-01-15\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM baby", Integer.class)).isEqualTo(1);

        mockMvc.perform(get("/api/baby"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("小宝贝"));

        // Validation: blank name and future DOB rejected.
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"dateOfBirth\":\"2030-01-01\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @Order(2)
    void babyPhotoUploadAndDownload() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        mockMvc.perform(multipart("/api/baby/photo")
                        .file(new MockMultipartFile("file", "baby.png", "image/png", png))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPhoto").value(true));

        MvcResult result = mockMvc.perform(get("/api/baby/photo"))
                .andExpect(status().isOk())
                .andExpect(header -> assertThat(header.getResponse().getContentType()).isEqualTo("image/png"))
                .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(png);

        // Unsupported type rejected with a stable code.
        mockMvc.perform(multipart("/api/baby/photo")
                        .file(new MockMultipartFile("file", "x.heic", "image/heic", png))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PHOTO_TYPE"));
    }

    @Test
    @Order(3)
    void growthRecordsCrudAndStandards() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/growth-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredOn\":\"2026-03-15\",\"weightKg\":5.4,\"heightCm\":58.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weightKg").value(5.4))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // Duplicate date rejected.
        mockMvc.perform(post("/api/growth-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredOn\":\"2026-03-15\",\"weightKg\":5.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DATE"));

        // Before-birth date rejected.
        mockMvc.perform(post("/api/growth-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredOn\":\"2025-12-01\",\"weightKg\":3.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEASURED_BEFORE_BIRTH"));

        // No measurement rejected.
        mockMvc.perform(post("/api/growth-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredOn\":\"2026-04-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_MEASUREMENT"));

        mockMvc.perform(get("/api/growth-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].measuredOn").value("2026-03-15"));

        mockMvc.perform(get("/api/growth-standards?gender=FEMALE&measure=WEIGHT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.curves.p50[0].ageMonths").value(0))
                .andExpect(jsonPath("$.curves.p50[0].value").value(3.2))
                .andExpect(jsonPath("$.curves.p97", org.hamcrest.Matchers.hasSize(37)));

        mockMvc.perform(delete("/api/growth-records/" + id))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(4)
    void milestoneSeedDataIsCompleteAndBilingual() throws Exception {
        Integer total = jdbcTemplate.queryForObject("SELECT count(*) FROM milestone_definition", Integer.class);
        assertThat(total).isEqualTo(127);

        Integer emptyTitles = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM milestone_definition WHERE title_en = '' OR title_zh = ''", Integer.class);
        assertThat(emptyTitles).isZero();

        // CDC checklist ages, all present.
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT age_months FROM milestone_definition ORDER BY age_months",
                Integer.class)).containsExactly(2, 4, 6, 9, 12, 15, 18, 24, 30, 36);

        mockMvc.perform(get("/api/milestones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ageMonths").value(2))
                .andExpect(jsonPath("$[0].milestones[0].titleZh").isNotEmpty());
    }

    @Test
    @Order(5)
    void milestoneAchievementLifecycle() throws Exception {
        mockMvc.perform(put("/api/milestones/2m-social-smiles/achievement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"achievedOn\":\"2026-03-20\",\"note\":\"第一次社交微笑\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.achievedOn").value("2026-03-20"))
                .andExpect(jsonPath("$.hasPhoto").value(false));

        mockMvc.perform(put("/api/milestones/2m-social-smiles/achievement/photo")
                        .file(new MockMultipartFile("file", "smile.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPhoto").value(true));

        mockMvc.perform(delete("/api/milestones/2m-social-smiles/achievement"))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/milestones/unknown-id/achievement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"achievedOn\":\"2026-03-20\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MILESTONE_NOT_FOUND"));
    }

    @Test
    @Order(6)
    void careLogsUseSgtDayBoundariesAndOffsetSerialization() throws Exception {
        // 2026-05-10 23:50 SGT
        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"loggedAt\":\"2026-05-10T23:50:00+08:00\",\"note\":\"奶150ml\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loggedAt").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("2026-05-10T23:50"),
                        org.hamcrest.Matchers.endsWith("+08:00"))));

        // Same instant expressed in UTC still lands on the SGT date 2026-05-10.
        mockMvc.perform(get("/api/care-logs?date=2026-05-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("FEEDING"))
                .andExpect(jsonPath("$[0].loggedAt").value(org.hamcrest.Matchers.endsWith("+08:00")));

        // The next SGT day does not include it.
        mockMvc.perform(get("/api/care-logs?date=2026-05-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        // Future timestamps rejected.
        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SLEEP\",\"loggedAt\":\"2030-01-01T00:00:00+08:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOGGED_AT_IN_FUTURE"));
    }
}
