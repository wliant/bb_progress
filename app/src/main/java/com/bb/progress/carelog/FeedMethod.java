package com.bb.progress.carelog;

/**
 * How a feed was given. {@code BREAST} and {@code BOTH} may carry minutes at the breast;
 * all three may carry a volume, and a volume is never implied by the method alone — a
 * breastfeed whose amount was not measured keeps a null amount rather than a zero.
 */
public enum FeedMethod {
    BREAST,
    BOTTLE,
    BOTH
}
