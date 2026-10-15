package io.vurli.link;

import java.util.List;

/**
 * One page of links, newest first.
 * @param hasNext whether a following page has at least one link
 */
public record LinkPage(List<LinkView> items, int page, int size, boolean hasNext) {
}
