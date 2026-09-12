package com.bb.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

    @Autowired
    software.amazon.awssdk.services.s3.S3Client s3;

    @Autowired
    com.bb.progress.photo.S3Properties s3Properties;

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

        mockMvc.perform(multipart("/api/milestones/2m-social-smiles/achievement/photo")
                        .file(new MockMultipartFile("file", "smile.jpg", "image/jpeg", tinyJpeg()))
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

    /** Every error, including the ones Spring raises before a controller runs, carries a code. */
    @Test
    @Order(7)
    void malformedRequestsAnswerInTheDocumentedErrorShape() throws Exception {
        mockMvc.perform(get("/api/growth-standards?measure=WEIGHT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));

        mockMvc.perform(get("/api/growth-standards?gender=ALIEN&measure=WEIGHT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(get("/api/care-logs?date=13-13-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(post("/api/growth-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(multipart("/api/baby/photo")
                        .file(new MockMultipartFile("wrongName", "x.jpg", "image/jpeg", new byte[]{1}))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_FILE"));
    }

    @Test
    @Order(8)
    void dateOfBirthCannotMovePastExistingRecords() throws Exception {
        mockMvc.perform(post("/api/growth-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredOn\":\"2026-02-01\",\"weightKg\":4.2}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小宝贝\",\"dateOfBirth\":\"2026-06-01\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOB_AFTER_RECORDS"));

        // The profile is unchanged and an earlier date is still accepted.
        mockMvc.perform(get("/api/baby"))
                .andExpect(jsonPath("$.dateOfBirth").value("2026-01-15"));
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小宝贝\",\"dateOfBirth\":\"2026-01-10\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小宝贝\",\"dateOfBirth\":\"2026-01-15\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(9)
    void growthRecordCanBeFetchedById() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/growth-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredOn\":\"2026-05-05\",\"heightCm\":64.0}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/growth-records/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measuredOn").value("2026-05-05"));

        mockMvc.perform(get("/api/growth-records/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROWTH_RECORD_NOT_FOUND"));
    }

    /** A replaced photo must not be served from the browser's cache. */
    @Test
    @Order(10)
    void photosAreServedWithRevalidationValidators() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/baby/photo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")))
                .andExpect(header().exists("ETag"))
                .andReturn();
        String etag = first.getResponse().getHeader("ETag");

        mockMvc.perform(get("/api/baby/photo").header("If-None-Match", etag))
                .andExpect(status().isNotModified());

        // Uploading a different photo changes the validator, so the cached copy is dropped.
        mockMvc.perform(multipart("/api/baby/photo")
                        .file(new MockMultipartFile("file", "next.png", "image/png", new byte[]{9, 8, 7}))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoVersion").isNotEmpty());

        mockMvc.perform(get("/api/baby/photo").header("If-None-Match", etag))
                .andExpect(status().isOk());
    }

    /** Two taps on "mark achieved" settle on the same state instead of one failing. */
    @Test
    @Order(11)
    void repeatedMilestoneCheckOffIsIdempotent() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(put("/api/milestones/4m-motor-holds-toy/achievement")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"achievedOn\":\"2026-05-20\",\"note\":\"再试一次\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.achievedOn").value("2026-05-20"));
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM milestone_achievement WHERE milestone_id = '4m-motor-holds-toy'",
                Integer.class)).isEqualTo(1);

        // A photo survives a later edit of the date/note.
        mockMvc.perform(multipart("/api/milestones/4m-motor-holds-toy/achievement/photo")
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", tinyJpeg()))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPhoto").value(true))
                .andExpect(jsonPath("$.photoVersion").isNotEmpty());

        mockMvc.perform(put("/api/milestones/4m-motor-holds-toy/achievement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"achievedOn\":\"2026-05-21\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPhoto").value(true));
    }

    /** Runs last: wipes the profile, so it also covers the "no profile yet" preconditions. */
    @Test
    @Order(12)
    void resetClearsEverythingAndWritesThenRequireAProfile() throws Exception {
        mockMvc.perform(delete("/api/baby")).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/baby"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BABY_NOT_FOUND"));
        mockMvc.perform(get("/api/growth-records"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(get("/api/care-logs"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM milestone_achievement", Integer.class))
                .isZero();
        // Definitions are reference data and must survive a reset.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM milestone_definition", Integer.class))
                .isEqualTo(127);

        // Every write now reports the same missing-profile precondition.
        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BABY_NOT_FOUND"));
        mockMvc.perform(post("/api/growth-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredOn\":\"2026-05-05\",\"weightKg\":6}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BABY_NOT_FOUND"));
        mockMvc.perform(put("/api/milestones/2m-social-calms/achievement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"achievedOn\":\"2026-03-01\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BABY_NOT_FOUND"));
    }

    /** Birth details are captured on the profile; measurements become the first growth point. */
    @Test
    @Order(13)
    void profileCapturesTimeOfBirthAndBirthMeasurements() throws Exception {
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"小宝","dateOfBirth":"2026-01-15","timeOfBirth":"14:30",
                                 "gender":"FEMALE","birthWeightKg":3.25,"birthLengthCm":49.5,
                                 "birthHeadCircumferenceCm":34.0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeOfBirth").value("14:30:00"))
                .andExpect(jsonPath("$.birthWeightKg").value(3.25))
                .andExpect(jsonPath("$.birthLengthCm").value(49.5))
                .andExpect(jsonPath("$.birthHeadCircumferenceCm").value(34.0));

        mockMvc.perform(get("/api/baby"))
                .andExpect(jsonPath("$.timeOfBirth").value("14:30:00"))
                .andExpect(jsonPath("$.birthWeightKg").value(3.25));

        // Stored once, as the birth-flagged growth record on the date of birth.
        mockMvc.perform(get("/api/growth-records"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].measuredOn").value("2026-01-15"))
                .andExpect(jsonPath("$[0].birth").value(true))
                .andExpect(jsonPath("$[0].heightCm").value(49.5));
    }

    @Test
    @Order(14)
    void correctingTheDateOfBirthMovesTheBirthRecordInsteadOfBeingRejected() throws Exception {
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"小宝","dateOfBirth":"2026-01-20","timeOfBirth":"14:30",
                                 "gender":"FEMALE","birthWeightKg":3.25,"birthLengthCm":49.5}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/growth-records"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].measuredOn").value("2026-01-20"))
                .andExpect(jsonPath("$[0].birth").value(true));

        // A later ordinary record still blocks moving the date of birth past it.
        mockMvc.perform(post("/api/growth-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredOn\":\"2026-02-10\",\"weightKg\":4.4}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"小宝","dateOfBirth":"2026-03-01","gender":"FEMALE",
                                 "birthWeightKg":3.25}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOB_AFTER_RECORDS"));
    }

    @Test
    @Order(15)
    void clearingBirthMeasurementsRemovesTheBirthRecordButKeepsOtherRecords() throws Exception {
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小宝\",\"dateOfBirth\":\"2026-01-20\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthWeightKg").doesNotExist())
                .andExpect(jsonPath("$.timeOfBirth").doesNotExist());

        mockMvc.perform(get("/api/growth-records"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].measuredOn").value("2026-02-10"))
                .andExpect(jsonPath("$[0].birth").value(false));
    }

    /** A photo straight off a phone is accepted and stored small. */
    @Test
    @Order(16)
    void careLogPhotoIsAcceptedAtPhoneSizeAndStoredUnderOneMegabyte() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"note\":\"150ml\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasPhoto").value(false))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        byte[] bigPhoto = largeNoisyPng(4032, 3024);
        assertThat(bigPhoto.length).isGreaterThan(5_000_000);

        mockMvc.perform(multipart("/api/care-logs/" + id + "/photo")
                        .file(new MockMultipartFile("file", "IMG_0001.png", "image/png", bigPhoto))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPhoto").value(true))
                .andExpect(jsonPath("$.photoVersion").isNotEmpty())
                // The note survives a photo upload.
                .andExpect(jsonPath("$.note").value("150ml"));

        MvcResult served = mockMvc.perform(get("/api/care-logs/" + id + "/photo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")))
                .andExpect(header().exists("ETag"))
                .andReturn();
        byte[] stored = served.getResponse().getContentAsByteArray();
        assertThat(stored.length).isLessThanOrEqualTo(1_048_576);
        assertThat(served.getResponse().getContentType()).isEqualTo("image/jpeg");

        // The list carries the photo flag so rows can show a thumbnail.
        mockMvc.perform(get("/api/care-logs"))
                .andExpect(jsonPath("$[0].hasPhoto").value(true));

        mockMvc.perform(delete("/api/care-logs/" + id + "/photo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPhoto").value(false));
        mockMvc.perform(get("/api/care-logs/" + id + "/photo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"));
    }

    @Test
    @Order(17)
    void careLogPhotoRejectsNonImagesAndUnsupportedTypes() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIAPER\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(multipart("/api/care-logs/" + id + "/photo")
                        .file(new MockMultipartFile("file", "x.heic", "image/heic", new byte[] {1, 2, 3}))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PHOTO_TYPE"));

        // Right content type, but the bytes are not decodable.
        mockMvc.perform(multipart("/api/care-logs/" + id + "/photo")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", "junk".getBytes()))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_UNREADABLE"));

        mockMvc.perform(multipart("/api/care-logs/00000000-0000-0000-0000-000000000000/photo")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", tinyJpeg()))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARE_LOG_NOT_FOUND"));
    }

    /** Deleting an entry must not leave its object behind in storage. */
    @Test
    @Order(18)
    void deletingACareLogRemovesItsStoredObject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SLEEP\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(multipart("/api/care-logs/" + id + "/photo")
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", tinyJpeg()))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk());

        long before = countStoredPhotos("care-logs");
        mockMvc.perform(delete("/api/care-logs/" + id)).andExpect(status().isNoContent());
        assertThat(countStoredPhotos("care-logs")).isEqualTo(before - 1);
    }

    /** The profile photo is the one image kept exactly as uploaded. */
    @Test
    @Order(19)
    void profilePhotoIsStoredWithoutRecompression() throws Exception {
        byte[] original = largeNoisyPng(1200, 900);
        mockMvc.perform(multipart("/api/baby/photo")
                        .file(new MockMultipartFile("file", "me.png", "image/png", original))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk());

        MvcResult served = mockMvc.perform(get("/api/baby/photo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andReturn();
        assertThat(served.getResponse().getContentAsByteArray()).isEqualTo(original);
    }

    /** Gestational age turns the birth measurements into a position on the newborn standard. */
    @Test
    @Order(20)
    void newbornAssessmentNeedsBothGestationalAgeAndBirthMeasurements() throws Exception {
        mockMvc.perform(delete("/api/baby")).andExpect(status().isNoContent());

        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小宝\",\"dateOfBirth\":\"2026-01-15\",\"gender\":\"MALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gestationalAgeDays").doesNotExist());

        mockMvc.perform(get("/api/newborn-assessment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GESTATIONAL_AGE_NOT_SET"));

        // Gestational age but still no measurements.
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"小宝","dateOfBirth":"2026-01-15","gender":"MALE",
                                 "gestationalAgeDays":280}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gestationalAgeDays").value(280));
        mockMvc.perform(get("/api/newborn-assessment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BIRTH_MEASUREMENTS_NOT_SET"));
    }

    @Test
    @Order(21)
    void newbornAssessmentPlacesBirthMeasurementsOnTheStandard() throws Exception {
        // A boy at exactly 40+0 weeks with the published median weight sits at the 50th centile.
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"小宝","dateOfBirth":"2026-01-15","gender":"MALE",
                                 "gestationalAgeDays":280,"birthWeightKg":3.38,
                                 "birthLengthCm":49.9,"birthHeadCircumferenceCm":34.3}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/newborn-assessment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gestationalAgeDays").value(280))
                .andExpect(jsonPath("$.covered").value(true))
                .andExpect(jsonPath("$.standard", org.hamcrest.Matchers.containsString("INTERGROWTH")))
                .andExpect(jsonPath("$.assessments", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.assessments[0].measure").value("WEIGHT"))
                .andExpect(jsonPath("$.assessments[0].centile",
                        org.hamcrest.Matchers.closeTo(50.0, 1.0)))
                .andExpect(jsonPath("$.assessments[0].zScore",
                        org.hamcrest.Matchers.closeTo(0.0, 0.05)))
                .andExpect(jsonPath("$.assessments[1].measure").value("HEIGHT"))
                .andExpect(jsonPath("$.assessments[2].measure").value("HEAD_CIRCUMFERENCE"));
    }

    @Test
    @Order(22)
    void veryPretermGestationsAreReportedAsOutsideTheBundledStandard() throws Exception {
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"小宝","dateOfBirth":"2026-01-15","gender":"MALE",
                                 "gestationalAgeDays":210,"birthWeightKg":1.2}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/newborn-assessment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.covered").value(false))
                .andExpect(jsonPath("$.assessments", org.hamcrest.Matchers.hasSize(0)));

        // Implausible gestations are rejected outright.
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"小宝","dateOfBirth":"2026-01-15","gender":"MALE",
                                 "gestationalAgeDays":400}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /** The gallery is a view over the other features' photos, newest first. */
    @Test
    @Order(23)
    void photoGalleryCollectsEveryPhotoWithItsContext() throws Exception {
        mockMvc.perform(delete("/api/baby")).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小宝\",\"dateOfBirth\":\"2026-01-15\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        // A care-log photo.
        MvcResult log = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"loggedAt\":\"2026-05-10T09:30:00+08:00\",\"note\":\"150ml\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String logId = com.jayway.jsonpath.JsonPath.read(log.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(multipart("/api/care-logs/" + logId + "/photo")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", tinyJpeg()))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk());

        // A milestone photo, achieved on a later date so it sorts first.
        mockMvc.perform(put("/api/milestones/2m-social-smiles/achievement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"achievedOn\":\"2026-06-01\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/milestones/2m-social-smiles/achievement/photo")
                        .file(new MockMultipartFile("file", "b.jpg", "image/jpeg", tinyJpeg()))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk());

        // And the profile photo, which has no date and therefore leads.
        mockMvc.perform(multipart("/api/baby/photo")
                        .file(new MockMultipartFile("file", "me.jpg", "image/jpeg", tinyJpeg()))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$[0].source").value("PROFILE"))
                .andExpect(jsonPath("$[0].takenOn").doesNotExist())
                .andExpect(jsonPath("$[1].source").value("MILESTONE"))
                .andExpect(jsonPath("$[1].takenOn").value("2026-06-01"))
                .andExpect(jsonPath("$[1].titleZh").value("你对宝宝说话或微笑时会报以微笑"))
                .andExpect(jsonPath("$[1].titleEn").isNotEmpty())
                .andExpect(jsonPath("$[2].source").value("CARE_LOG"))
                .andExpect(jsonPath("$[2].takenOn").value("2026-05-10"))
                .andExpect(jsonPath("$[2].careType").value("FEEDING"))
                .andExpect(jsonPath("$[2].note").value("150ml"))
                .andExpect(jsonPath("$[2].thumbnailUrl",
                        org.hamcrest.Matchers.containsString("size=thumb")));

        // Removing a photo removes it from the gallery.
        mockMvc.perform(delete("/api/care-logs/" + logId + "/photo")).andExpect(status().isOk());
        mockMvc.perform(get("/api/photos"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    @Order(24)
    void thumbnailsAreSmallerThanTheOriginalAndCachedSeparately() throws Exception {
        byte[] photo = largeNoisyPng(1600, 1200);
        MvcResult created = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SLEEP\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(multipart("/api/care-logs/" + id + "/photo")
                        .file(new MockMultipartFile("file", "big.png", "image/png", photo))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk());

        byte[] full = mockMvc.perform(get("/api/care-logs/" + id + "/photo"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        MvcResult thumbResult = mockMvc.perform(get("/api/care-logs/" + id + "/photo?size=thumb"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andReturn();
        byte[] thumb = thumbResult.getResponse().getContentAsByteArray();

        assertThat(thumb.length).isLessThan(full.length);
        assertThat(thumb.length).isLessThanOrEqualTo(120_000);

        // A thumbnail must not be served from the full image's cache entry, and vice versa.
        String thumbEtag = thumbResult.getResponse().getHeader("ETag");
        mockMvc.perform(get("/api/care-logs/" + id + "/photo?size=thumb").header("If-None-Match", thumbEtag))
                .andExpect(status().isNotModified());
        mockMvc.perform(get("/api/care-logs/" + id + "/photo").header("If-None-Match", thumbEtag))
                .andExpect(status().isOk());

        // Deleting the entry takes the cached thumbnail with it.
        mockMvc.perform(delete("/api/care-logs/" + id)).andExpect(status().isNoContent());
        assertThat(countStoredPhotos("care-logs")).isZero();
    }

    /** A reset deletes rows in bulk, so every photo must be removed explicitly alongside them. */
    @Test
    @Order(25)
    void resetLeavesNoOrphanedObjects() throws Exception {
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小宝\",\"dateOfBirth\":\"2026-01-15\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/baby/photo")
                        .file(new MockMultipartFile("file", "me.jpg", "image/jpeg", tinyJpeg()))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk());

        MvcResult log = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String logId = com.jayway.jsonpath.JsonPath.read(log.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(multipart("/api/care-logs/" + logId + "/photo")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", tinyJpeg()))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk());
        // Viewing the gallery materialises a cached thumbnail, which must also go.
        mockMvc.perform(get("/api/care-logs/" + logId + "/photo?size=thumb")).andExpect(status().isOk());

        mockMvc.perform(put("/api/milestones/2m-social-smiles/achievement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"achievedOn\":\"2026-03-20\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/milestones/2m-social-smiles/achievement/photo")
                        .file(new MockMultipartFile("file", "b.jpg", "image/jpeg", tinyJpeg()))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk());

        assertThat(countStoredPhotos("care-logs")).isPositive();

        mockMvc.perform(delete("/api/baby")).andExpect(status().isNoContent());

        assertThat(countStoredPhotos("baby")).isZero();
        assertThat(countStoredPhotos("care-logs")).isZero();
        assertThat(countStoredPhotos("milestones")).isZero();
    }

    private static byte[] largeNoisyPng(int width, int height) throws Exception {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(7);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] tinyJpeg() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(
                new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", out);
        return out.toByteArray();
    }

    /** Objects stored under a key prefix, thumbnails included. */
    private long countStoredPhotos(String prefix) {
        return s3.listObjectsV2(software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder()
                        .bucket(s3Properties.bucket())
                        .prefix(prefix + "/")
                        .build())
                .contents().size();
    }
}
