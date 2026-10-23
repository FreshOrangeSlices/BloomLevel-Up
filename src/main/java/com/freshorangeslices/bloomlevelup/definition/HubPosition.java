package com.freshorangeslices.bloomlevelup.definition;

/**
 * Where a Major sits on its tab's page in the Skill Hub, in pixels of the tab's background picture
 * (0,0 = its top-left corner; the page is 416x320). Lets a Major be placed to fit the art.
 */
public record HubPosition(int x, int y) {
}
