package com.oracleos.app.feature.maps

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Builds a fully offline MapLibre style for one or more local PMTiles archives.
 *
 * No remote URL is referenced: tiles come from pmtiles://file://, glyphs from the APK assets.
 * Layer names follow companion/map-pack-builder/gb-schema.template.yml; the older London schema
 * uses a subset of the same names, so both render with the same rules.
 */
object MapStyle {
    const val SAVED_SOURCE = "saved-places"
    private const val FONT = "NotoSansRegular"
    private const val FONT_BOLD = "NotoSansBold"

    fun build(maps: List<File>): String {
        val sources = JSONObject()
        val layers = JSONArray()
        layers.put(JSONObject("""{"id":"background","type":"background","paint":{"background-color":"#0B1118"}}"""))
        maps.forEachIndexed { index, file ->
            val source = "map$index"
            sources.put(source, JSONObject()
                .put("type", "vector")
                .put("url", "pmtiles://file://${file.absolutePath}")
                .put("attribution", "© OpenStreetMap contributors"))
            fillAndLineLayers(source).forEach { layers.put(JSONObject(it)) }
        }
        maps.indices.forEach { index -> labelLayers("map$index").forEach { layers.put(JSONObject(it)) } }
        sources.put(SAVED_SOURCE, JSONObject("""{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}"""))
        layers.put(JSONObject("""{"id":"saved-circle","type":"circle","source":"$SAVED_SOURCE",
            "paint":{"circle-radius":7,"circle-color":"#38D89D","circle-stroke-color":"#FFFFFF","circle-stroke-width":2}}"""))
        layers.put(JSONObject("""{"id":"saved-label","type":"symbol","source":"$SAVED_SOURCE",
            "layout":{"text-field":["get","name"],"text-font":["$FONT_BOLD"],"text-size":12,"text-offset":[0,1.3],"text-anchor":"top"},
            "paint":{"text-color":"#38D89D","text-halo-color":"#090D12","text-halo-width":1.5}}"""))
        return JSONObject()
            .put("version", 8)
            .put("name", "Oracle Dark Offline")
            .put("glyphs", "asset://glyphs/{fontstack}/{range}.pbf")
            .put("sources", sources)
            .put("layers", layers)
            .toString()
    }

    private val ROAD_COLOR = """["match",["get","highway"],
        ["motorway","motorway_link","trunk","trunk_link"],"#F5B84B",
        ["primary","primary_link"],"#C9A85C",
        ["secondary","secondary_link","tertiary","tertiary_link"],"#8A949E",
        ["path","footway","cycleway","bridleway","steps","track"],"#4F5A64",
        "#68737D"]"""

    private val ROAD_WIDTH = """["interpolate",["linear"],["zoom"],
        5,["match",["get","highway"],["motorway","trunk"],1.2,0.4],
        10,["match",["get","highway"],["motorway","trunk"],2.6,["primary"],1.8,["secondary","tertiary"],1.0,0.5],
        14,["match",["get","highway"],["motorway","trunk"],5.0,["primary"],4.0,["secondary","tertiary"],3.0,["path","footway","cycleway","bridleway","steps"],1.0,2.0],
        17,["match",["get","highway"],["motorway","trunk"],10.0,["primary"],9.0,["path","footway","cycleway","bridleway","steps"],2.0,6.0]]"""

    private val POI_KIND = """["coalesce",["get","amenity"],["get","healthcare"],["get","emergency"],["get","man_made"],["get","natural"],""]"""

    private val POI_COLOR = """["match",$POI_KIND,
        ["hospital","clinic","doctors","doctor","ambulance_station"],"#E66B6B",
        ["pharmacy"],"#38D89D",
        ["police"],"#5B8DEF",
        ["fire_station"],"#F08A3C",
        ["drinking_water","water_tap","water_well","spring"],"#4FC3F7",
        ["shelter","assembly_point"],"#F5B84B",
        ["defibrillator"],"#FF4D6D",
        "#9AA4AE"]"""

    private fun fillAndLineLayers(s: String) = listOf(
        """{"id":"$s-green","type":"fill","source":"$s","source-layer":"green","paint":{"fill-color":"#16291F","fill-opacity":0.85}}""",
        """{"id":"$s-water","type":"fill","source":"$s","source-layer":"water","paint":{"fill-color":"#17344A"}}""",
        """{"id":"$s-waterways","type":"line","source":"$s","source-layer":"waterways",
            "paint":{"line-color":"#2D5C72","line-width":["interpolate",["linear"],["zoom"],8,0.6,14,2.0]}}""",
        """{"id":"$s-coastline","type":"line","source":"$s","source-layer":"coastline","paint":{"line-color":"#3A7A96","line-width":1.4}}""",
        """{"id":"$s-buildings","type":"fill","source":"$s","source-layer":"buildings","minzoom":14,
            "paint":{"fill-color":"#222B36","fill-outline-color":"#2C3744"}}""",
        """{"id":"$s-boundaries","type":"line","source":"$s","source-layer":"boundaries",
            "paint":{"line-color":"#7A6AA0","line-dasharray":[3,2],"line-width":["match",["get","admin_level"],"2",1.6,1.0]}}""",
        """{"id":"$s-railways","type":"line","source":"$s","source-layer":"railways",
            "paint":{"line-color":"#5A6068","line-dasharray":[2,2],"line-width":["interpolate",["linear"],["zoom"],8,0.6,14,1.8]}}""",
        """{"id":"$s-roads","type":"line","source":"$s","source-layer":"roads",
            "layout":{"line-cap":"round","line-join":"round"},
            "paint":{"line-color":$ROAD_COLOR,"line-width":$ROAD_WIDTH}}""",
        """{"id":"$s-poi","type":"circle","source":"$s","source-layer":"essential_poi",
            "paint":{"circle-color":$POI_COLOR,"circle-radius":["interpolate",["linear"],["zoom"],11,3,16,7],
              "circle-stroke-color":"#090D12","circle-stroke-width":1}}""",
        """{"id":"$s-peaks","type":"circle","source":"$s","source-layer":"peaks","minzoom":11,
            "paint":{"circle-color":"#B08D57","circle-radius":2.5}}""",
    )

    private fun labelLayers(s: String) = listOf(
        """{"id":"$s-road-ref","type":"symbol","source":"$s","source-layer":"roads","minzoom":8,
            "filter":["has","ref"],
            "layout":{"symbol-placement":"line","text-field":["get","ref"],"text-font":["$FONT_BOLD"],"text-size":10,
              "symbol-spacing":400,"text-rotation-alignment":"viewport"},
            "paint":{"text-color":"#F5B84B","text-halo-color":"#090D12","text-halo-width":1.5}}""",
        """{"id":"$s-road-name","type":"symbol","source":"$s","source-layer":"roads","minzoom":14,
            "filter":["has","name"],
            "layout":{"symbol-placement":"line","text-field":["get","name"],"text-font":["$FONT"],"text-size":11},
            "paint":{"text-color":"#C9D1D9","text-halo-color":"#090D12","text-halo-width":1.5}}""",
        """{"id":"$s-water-name","type":"symbol","source":"$s","source-layer":"waterways","minzoom":12,
            "filter":["has","name"],
            "layout":{"symbol-placement":"line","text-field":["get","name"],"text-font":["$FONT"],"text-size":10},
            "paint":{"text-color":"#6FA8C4","text-halo-color":"#090D12","text-halo-width":1.2}}""",
        """{"id":"$s-peak-name","type":"symbol","source":"$s","source-layer":"peaks","minzoom":12,
            "filter":["has","name"],
            "layout":{"text-field":["concat",["get","name"]," ",["coalesce",["get","ele"],""]],"text-font":["$FONT"],
              "text-size":10,"text-offset":[0,0.9],"text-anchor":"top"},
            "paint":{"text-color":"#C9A97A","text-halo-color":"#090D12","text-halo-width":1.2}}""",
        """{"id":"$s-poi-name","type":"symbol","source":"$s","source-layer":"essential_poi","minzoom":14,
            "filter":["has","name"],
            "layout":{"text-field":["get","name"],"text-font":["$FONT"],"text-size":10,"text-offset":[0,1.0],"text-anchor":"top",
              "text-optional":true},
            "paint":{"text-color":$POI_COLOR,"text-halo-color":"#090D12","text-halo-width":1.4}}""",
        """{"id":"$s-places","type":"symbol","source":"$s","source-layer":"places",
            "filter":["has","name"],
            "layout":{"text-field":["get","name"],
              "text-font":["match",["get","place"],["city","town"],["literal",["$FONT_BOLD"]],["literal",["$FONT"]]],
              "text-size":["match",["get","place"],"city",16,"town",13,["village","suburb"],11,10],
              "symbol-sort-key":["match",["get","place"],"city",0,"town",1,"village",2,3],
              "text-padding":4},
            "paint":{"text-color":["match",["get","place"],["city","town"],"#F2F5F7","#B7C1CA"],
              "text-halo-color":"#090D12","text-halo-width":1.8}}""",
    )
}
