package com.board.gamekit.bgg

import com.board.gamekit.model.GameDto
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object BggXmlParser {

    private val factory: DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

    fun parseSearch(xml: String): List<GameDto> = items(xml).mapNotNull { item ->
        val id = item.getAttribute("id").toIntOrNull() ?: return@mapNotNull null
        val name = item.primaryName() ?: return@mapNotNull null
        GameDto(
            bggId = id,
            name = name,
            yearPublished = item.childValue("yearpublished")?.toIntOrNull(),
        )
    }

    fun parseThings(xml: String): Map<Int, GameDto> = items(xml).mapNotNull { item ->
        val id = item.getAttribute("id").toIntOrNull() ?: return@mapNotNull null
        val name = item.primaryName() ?: return@mapNotNull null
        id to GameDto(
            bggId = id,
            name = name,
            yearPublished = item.childValue("yearpublished")?.toIntOrNull(),
            imageUrl = item.childText("image") ?: item.childText("thumbnail"),
            minPlayers = item.childValue("minplayers")?.toIntOrNull(),
            maxPlayers = item.childValue("maxplayers")?.toIntOrNull(),
            playingTime = item.childValue("playingtime")?.toIntOrNull(),
            rating = item.averageRating(),
        )
    }.toMap()

    private fun items(xml: String): List<Element> {
        val document = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.trim().toByteArray(Charsets.UTF_8)))
        return document.documentElement
            .getElementsByTagName("item")
            .asElementList()
            .filter { it.parentNode === document.documentElement }
    }

    private fun Element.primaryName(): String? {
        val names = getElementsByTagName("name").asElementList()
        val primary = names.firstOrNull { it.getAttribute("type") == "primary" } ?: names.firstOrNull()
        return primary?.getAttribute("value")?.takeIf { it.isNotBlank() }
    }

    private fun Element.childValue(tag: String): String? =
        directChild(tag)?.getAttribute("value")?.takeIf { it.isNotBlank() }

    private fun Element.childText(tag: String): String? =
        directChild(tag)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun Element.averageRating(): Double? =
        directChild("statistics")
            ?.directChild("ratings")
            ?.directChild("average")
            ?.getAttribute("value")
            ?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }

    private fun Element.directChild(tag: String): Element? =
        childNodes.asElementList().firstOrNull { it.tagName == tag }

    private fun org.w3c.dom.NodeList.asElementList(): List<Element> =
        (0 until length).mapNotNull { index ->
            item(index).takeIf { it.nodeType == Node.ELEMENT_NODE } as? Element
        }
}
