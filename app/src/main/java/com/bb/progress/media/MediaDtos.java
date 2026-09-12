package com.bb.progress.media;

import java.util.UUID;

public final class MediaDtos {

    private MediaDtos() {
    }

    /**
     * @param thumbnailUrl null for video and audio — the app has no transcoder, so it shows a
     *                     kind icon rather than inventing a frame.
     */
    public record MediaView(
            UUID id,
            MediaKind kind,
            String contentType,
            String url,
            String thumbnailUrl) {

        public static MediaView from(Media media) {
            String url = "/api/media/" + media.getId() + "/content";
            return new MediaView(media.getId(), media.getKind(), media.getContentType(), url,
                    media.getKind() == MediaKind.PHOTO ? url + "?size=thumb" : null);
        }
    }
}
