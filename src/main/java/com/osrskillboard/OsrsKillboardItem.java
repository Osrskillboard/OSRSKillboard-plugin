package com.osrskillboard;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
class OsrsKillboardItem
{
    private final int id;
    private final String name;
    private final int quantity;
    private final long gePrice;
}
