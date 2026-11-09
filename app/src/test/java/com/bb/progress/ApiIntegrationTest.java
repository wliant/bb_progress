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
                .andExpect(jsonPath("$.media", org.hamcrest.Matchers.hasSize(0)));

        mockMvc.perform(multipart("/api/milestones/2m-social-smiles/achievement/media")
                        .file(new MockMultipartFile("files", "smile.jpg", "image/jpeg", tinyJpeg())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].kind").value("PHOTO"));

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
        mockMvc.perform(multipart("/api/milestones/4m-motor-holds-toy/achievement/media")
                        .file(new MockMultipartFile("files", "p.jpg", "image/jpeg", tinyJpeg())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(put("/api/milestones/4m-motor-holds-toy/achievement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"achievedOn\":\"2026-05-21\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media", org.hamcrest.Matchers.hasSize(1)));
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
    void careLogMediaAcceptsPhotoVideoAndVoice() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"note\":\"150ml\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media", org.hamcrest.Matchers.hasSize(0)))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        byte[] bigPhoto = largeNoisyPng(4032, 3024);
        assertThat(bigPhoto.length).isGreaterThan(5_000_000);

        // Several files at once, one of each kind.
        MvcResult uploaded = mockMvc.perform(multipart("/api/care-logs/" + id + "/media")
                        .file(new MockMultipartFile("files", "IMG_0001.png", "image/png", bigPhoto))
                        .file(new MockMultipartFile("files", "clip.mp4", "video/mp4", "fake-video-bytes".getBytes()))
                        .file(new MockMultipartFile("files", "note.m4a", "audio/mp4", "fake-audio-bytes".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$[0].kind").value("PHOTO"))
                .andExpect(jsonPath("$[1].kind").value("VIDEO"))
                .andExpect(jsonPath("$[2].kind").value("AUDIO"))
                // Only photos get a thumbnail; there is no transcoder to make one for the rest.
                .andExpect(jsonPath("$[0].thumbnailUrl").isNotEmpty())
                .andExpect(jsonPath("$[1].thumbnailUrl").doesNotExist())
                .andReturn();
        String body = uploaded.getResponse().getContentAsString();
        String photoId = com.jayway.jsonpath.JsonPath.read(body, "$[0].id");
        String videoId = com.jayway.jsonpath.JsonPath.read(body, "$[1].id");
        String audioId = com.jayway.jsonpath.JsonPath.read(body, "$[2].id");

        // The note survives the upload and the entry carries all three.
        mockMvc.perform(get("/api/care-logs"))
                .andExpect(jsonPath("$[0].note").value("150ml"))
                .andExpect(jsonPath("$[0].media", org.hamcrest.Matchers.hasSize(3)));

        MvcResult served = mockMvc.perform(get("/api/media/" + photoId + "/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")))
                .andExpect(header().exists("ETag"))
                .andReturn();
        assertThat(served.getResponse().getContentAsByteArray().length).isLessThanOrEqualTo(1_048_576);
        assertThat(served.getResponse().getContentType()).isEqualTo("image/jpeg");

        // Video and voice are served back exactly as uploaded, with their own content types.
        MvcResult video = mockMvc.perform(get("/api/media/" + videoId + "/content"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(video.getResponse().getContentType()).isEqualTo("video/mp4");
        assertThat(video.getResponse().getContentAsByteArray()).isEqualTo("fake-video-bytes".getBytes());
        mockMvc.perform(get("/api/media/" + audioId + "/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mp4"));

        // Removing one attachment leaves the others.
        mockMvc.perform(delete("/api/media/" + videoId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/care-logs/" + id + "/media"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
        mockMvc.perform(get("/api/media/" + videoId + "/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @Order(17)
    void careLogMediaRejectsUnsupportedTypes() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIAPER\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(multipart("/api/care-logs/" + id + "/media")
                        .file(new MockMultipartFile("files", "x.heic", "image/heic", new byte[] {1, 2, 3})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        mockMvc.perform(multipart("/api/care-logs/" + id + "/media")
                        .file(new MockMultipartFile("files", "doc.pdf", "application/pdf", new byte[] {1, 2})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        // Right content type, but the bytes are not a decodable image.
        mockMvc.perform(multipart("/api/care-logs/" + id + "/media")
                        .file(new MockMultipartFile("files", "x.jpg", "image/jpeg", "junk".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_UNREADABLE"));
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

        mockMvc.perform(multipart("/api/care-logs/" + id + "/media")
                        .file(new MockMultipartFile("files", "p.jpg", "image/jpeg", tinyJpeg())))
                .andExpect(status().isOk());

        long before = countStoredPhotos("media");
        mockMvc.perform(delete("/api/care-logs/" + id)).andExpect(status().isNoContent());
        assertThat(countStoredPhotos("media")).isEqualTo(before - 1);
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

        mockMvc.perform(get("/api/media"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        // A care-log photo.
        MvcResult log = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"loggedAt\":\"2026-05-10T09:30:00+08:00\",\"note\":\"150ml\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String logId = com.jayway.jsonpath.JsonPath.read(log.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(multipart("/api/care-logs/" + logId + "/media")
                        .file(new MockMultipartFile("files", "a.jpg", "image/jpeg", tinyJpeg()))
                        .file(new MockMultipartFile("files", "v.mp4", "video/mp4", "vid".getBytes())))
                .andExpect(status().isOk());

        // A milestone photo, achieved on a later date so it sorts first.
        mockMvc.perform(put("/api/milestones/2m-social-smiles/achievement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"achievedOn\":\"2026-06-01\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/milestones/2m-social-smiles/achievement/media")
                        .file(new MockMultipartFile("files", "b.jpg", "image/jpeg", tinyJpeg())))
                .andExpect(status().isOk());

        // And the profile photo, which has no date and therefore leads.
        mockMvc.perform(multipart("/api/baby/photo")
                        .file(new MockMultipartFile("file", "me.jpg", "image/jpeg", tinyJpeg()))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/media"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(4)))
                .andExpect(jsonPath("$[0].source").value("PROFILE"))
                .andExpect(jsonPath("$[0].kind").value("PHOTO"))
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
                        org.hamcrest.Matchers.containsString("size=thumb")))
                // The video from the same entry is listed too, without a thumbnail.
                .andExpect(jsonPath("$[3].kind").value("VIDEO"))
                .andExpect(jsonPath("$[3].thumbnailUrl").doesNotExist());

        // Deleting the entry takes its attachments out of the gallery.
        mockMvc.perform(delete("/api/care-logs/" + logId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/media"))
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
        MvcResult up = mockMvc.perform(multipart("/api/care-logs/" + id + "/media")
                        .file(new MockMultipartFile("files", "big.png", "image/png", photo)))
                .andExpect(status().isOk())
                .andReturn();
        String mediaId = com.jayway.jsonpath.JsonPath.read(up.getResponse().getContentAsString(), "$[0].id");

        byte[] full = mockMvc.perform(get("/api/media/" + mediaId + "/content"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        MvcResult thumbResult = mockMvc.perform(get("/api/media/" + mediaId + "/content?size=thumb"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andReturn();
        byte[] thumb = thumbResult.getResponse().getContentAsByteArray();

        assertThat(thumb.length).isLessThan(full.length);
        assertThat(thumb.length).isLessThanOrEqualTo(120_000);

        // A thumbnail must not be served from the full image's cache entry, and vice versa.
        String thumbEtag = thumbResult.getResponse().getHeader("ETag");
        mockMvc.perform(get("/api/media/" + mediaId + "/content?size=thumb").header("If-None-Match", thumbEtag))
                .andExpect(status().isNotModified());
        mockMvc.perform(get("/api/media/" + mediaId + "/content").header("If-None-Match", thumbEtag))
                .andExpect(status().isOk());

        // Deleting the entry takes the attachment and its cached thumbnail with it.
        long before = countStoredPhotos("media");
        mockMvc.perform(delete("/api/care-logs/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/media/" + mediaId + "/content"))
                .andExpect(status().isNotFound());
        // The original and its thumbnail, and nothing else.
        assertThat(countStoredPhotos("media")).isEqualTo(before - 2);
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
        MvcResult attached = mockMvc.perform(multipart("/api/care-logs/" + logId + "/media")
                        .file(new MockMultipartFile("files", "a.jpg", "image/jpeg", tinyJpeg()))
                        .file(new MockMultipartFile("files", "v.mp4", "video/mp4", "vid".getBytes())))
                .andExpect(status().isOk())
                .andReturn();
        String attachedId = com.jayway.jsonpath.JsonPath.read(
                attached.getResponse().getContentAsString(), "$[0].id");
        // Viewing the gallery materialises a cached thumbnail, which must also go.
        mockMvc.perform(get("/api/media/" + attachedId + "/content?size=thumb")).andExpect(status().isOk());

        mockMvc.perform(put("/api/milestones/2m-social-smiles/achievement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"achievedOn\":\"2026-03-20\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/milestones/2m-social-smiles/achievement/media")
                        .file(new MockMultipartFile("files", "b.jpg", "image/jpeg", tinyJpeg())))
                .andExpect(status().isOk());

        assertThat(countStoredPhotos("media")).isPositive();

        mockMvc.perform(delete("/api/baby")).andExpect(status().isNoContent());

        assertThat(countStoredPhotos("baby")).isZero();
        assertThat(countStoredPhotos("media")).isZero();
        assertThat(countStoredPhotos("media")).isZero();
    }

    /**
     * Every care-log write enforces the profile precondition, and the reset tests above
     * deliberately leave none behind, so the care-detail tests put one back first.
     */
    private void ensureProfile() throws Exception {
        mockMvc.perform(put("/api/baby")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\u5c0f\u5b9d\",\"dateOfBirth\":\"2026-01-15\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk());
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
    @Test
    @Order(26)
    void feedingRecordsAmountAndDurationTogether() throws Exception {
        ensureProfile();
        // The point of spec 08: a breastfeed carries minutes AND a volume, and a volume
        // that was never measured stays null rather than becoming a zero.
        MvcResult breast = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"loggedAt\":\"2026-06-01T08:00:00+08:00\","
                                + "\"method\":\"BREAST\",\"durationMin\":15,\"amountMl\":60}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("BREAST"))
                .andExpect(jsonPath("$.durationMin").value(15))
                .andExpect(jsonPath("$.amountMl").value(60))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(breast.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"loggedAt\":\"2026-06-01T11:00:00+08:00\","
                                + "\"method\":\"BREAST\",\"durationMin\":12}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMin").value(12))
                .andExpect(jsonPath("$.amountMl").doesNotExist());

        // An edit keeps both quantities round-tripping.
        mockMvc.perform(put("/api/care-logs/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loggedAt\":\"2026-06-01T08:00:00+08:00\",\"method\":\"BOTH\","
                                + "\"durationMin\":10,\"amountMl\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("BOTH"))
                .andExpect(jsonPath("$.durationMin").value(10))
                .andExpect(jsonPath("$.amountMl").value(40));
    }

    @Test
    @Order(27)
    void detailFieldsAreRejectedForTheWrongCareType() throws Exception {
        ensureProfile();
        // Mirrors the CHECK constraints in V10, so neither layer is the only thing holding
        // the rule — and the failure is the documented error contract, not a 500.
        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SLEEP\",\"amountMl\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CARE_DETAIL_TYPE_MISMATCH"));

        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"condition\":\"DIRTY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CARE_DETAIL_TYPE_MISMATCH"));

        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIAPER\",\"startedAt\":\"2026-06-01T08:00:00+08:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CARE_DETAIL_TYPE_MISMATCH"));

        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"method\":\"BOTTLE\",\"amountMl\":9999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @Order(28)
    void sleepTracksAWindowAndDerivesItsDuration() throws Exception {
        ensureProfile();
        MvcResult open = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SLEEP\",\"loggedAt\":\"2026-06-02T13:00:00+08:00\"}"))
                .andExpect(status().isCreated())
                // The start defaults to the entry's time, and an open nap has no duration.
                .andExpect(jsonPath("$.startedAt").value("2026-06-02T13:00:00+08:00"))
                .andExpect(jsonPath("$.endedAt").doesNotExist())
                .andExpect(jsonPath("$.sleepMinutes").doesNotExist())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(open.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/care-logs/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loggedAt\":\"2026-06-02T13:00:00+08:00\","
                                + "\"startedAt\":\"2026-06-02T13:00:00+08:00\","
                                + "\"endedAt\":\"2026-06-02T14:35:00+08:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sleepMinutes").value(95));

        // A backwards window is refused rather than stored.
        mockMvc.perform(put("/api/care-logs/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loggedAt\":\"2026-06-02T13:00:00+08:00\","
                                + "\"startedAt\":\"2026-06-02T13:00:00+08:00\","
                                + "\"endedAt\":\"2026-06-02T12:00:00+08:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SLEEP_WINDOW_INVALID"));

        // Ending an already-ended sleep is a conflict, not a silent overwrite.
        mockMvc.perform(post("/api/care-logs/" + id + "/end-sleep"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLEEP_ALREADY_ENDED"));
    }

    @Test
    @Order(29)
    void endSleepClosesAnOpenNapAtTheCurrentTime() throws Exception {
        ensureProfile();
        MvcResult open = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SLEEP\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(open.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/care-logs/" + id + "/end-sleep"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endedAt").exists())
                .andExpect(jsonPath("$.sleepMinutes").value(0));

        // Only a sleep can be ended.
        MvcResult feed = mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"method\":\"BOTTLE\",\"amountMl\":80}"))
                .andExpect(status().isCreated())
                .andReturn();
        String feedId = com.jayway.jsonpath.JsonPath.read(feed.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post("/api/care-logs/" + feedId + "/end-sleep"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CARE_LOG_NOT_SLEEP"));
    }

    @Test
    @Order(30)
    void diaperRecordsItsCondition() throws Exception {
        ensureProfile();
        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIAPER\",\"condition\":\"VERY_DIRTY\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.condition").value("VERY_DIRTY"));

        // A quick-logged change with nothing recorded is allowed.
        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIAPER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.condition").doesNotExist());
    }

    @Test
    @Order(31)
    void aggregatesReportCoverageRatherThanABareTotal() throws Exception {
        ensureProfile();
        jdbcTemplate.update("DELETE FROM care_log");

        // One measured bottle and one unmeasured breastfeed on the same SGT day: the day's
        // volume is real but incomplete, which is the case the whole view exists to show.
        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"loggedAt\":\"2026-07-01T09:00:00+08:00\","
                                + "\"method\":\"BOTTLE\",\"amountMl\":100}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"loggedAt\":\"2026-07-01T13:00:00+08:00\","
                                + "\"method\":\"BREAST\",\"durationMin\":18}"))
                .andExpect(status().isCreated());
        // 23:50 SGT stays on the 1st regardless of the server's own timezone.
        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIAPER\",\"loggedAt\":\"2026-07-01T23:50:00+08:00\","
                                + "\"condition\":\"SLIGHTLY_DIRTY\"}"))
                .andExpect(status().isCreated());
        // A fully measured second day, for contrast.
        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEEDING\",\"loggedAt\":\"2026-07-03T09:00:00+08:00\","
                                + "\"method\":\"BOTTLE\",\"amountMl\":120}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/care-aggregates?from=2026-07-01&to=2026-07-03"))
                .andExpect(status().isOk())
                // Every day in the window is present, so a gap is a real gap.
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].date").value("2026-07-01"))
                .andExpect(jsonPath("$.days[0].amountMl").value(100))
                .andExpect(jsonPath("$.days[0].feedsTotal").value(2))
                .andExpect(jsonPath("$.days[0].feedsWithAmount").value(1))
                .andExpect(jsonPath("$.days[0].breastMinutes").value(18))
                .andExpect(jsonPath("$.days[0].partial").value(true))
                .andExpect(jsonPath("$.days[0].diaperTotal").value(1))
                .andExpect(jsonPath("$.days[0].diaperSlightlyDirty").value(1))
                // An empty day is zeroed, and is not "partial" — nothing is missing.
                .andExpect(jsonPath("$.days[1].date").value("2026-07-02"))
                .andExpect(jsonPath("$.days[1].feedsTotal").value(0))
                .andExpect(jsonPath("$.days[1].partial").value(false))
                .andExpect(jsonPath("$.days[2].amountMl").value(120))
                .andExpect(jsonPath("$.days[2].partial").value(false))
                // Averages divide by days holding entries, not the width of the window.
                .andExpect(jsonPath("$.summary.amountMl").value(220))
                .andExpect(jsonPath("$.summary.feedsTotal").value(3))
                .andExpect(jsonPath("$.summary.feedsWithAmount").value(2))
                .andExpect(jsonPath("$.summary.daysWithData").value(2))
                .andExpect(jsonPath("$.summary.coveragePct").value(66.7))
                .andExpect(jsonPath("$.summary.avgAmountMlPerDay").value(110.0));
    }

    @Test
    @Order(32)
    void aggregateSumsSleepByTheDayItBeganAndSkipsOpenNaps() throws Exception {
        ensureProfile();
        jdbcTemplate.update("DELETE FROM care_log");

        // A nap crossing midnight counts on the day it started.
        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SLEEP\",\"startedAt\":\"2026-07-05T23:30:00+08:00\","
                                + "\"endedAt\":\"2026-07-06T01:00:00+08:00\","
                                + "\"loggedAt\":\"2026-07-05T23:30:00+08:00\"}"))
                .andExpect(status().isCreated());
        // An open nap contributes no minutes but is still counted as a session.
        mockMvc.perform(post("/api/care-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SLEEP\",\"loggedAt\":\"2026-07-05T14:00:00+08:00\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/care-aggregates?from=2026-07-05&to=2026-07-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].sleepMinutes").value(90))
                .andExpect(jsonPath("$.days[0].sleepSessions").value(2))
                .andExpect(jsonPath("$.days[0].openSleeps").value(1))
                .andExpect(jsonPath("$.days[1].sleepMinutes").value(0));
    }

    @Test
    @Order(33)
    void aggregateRejectsAnImpossibleRange() throws Exception {
        mockMvc.perform(get("/api/care-aggregates?from=2026-11-01&to=2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CARE_AGGREGATE_RANGE_INVALID"));

        mockMvc.perform(get("/api/care-aggregates?from=2020-01-01&to=2026-11-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CARE_AGGREGATE_RANGE_INVALID"));
    }
}
