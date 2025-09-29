package com.example.travelpractice.data

data class DefaultCategory(val title: String, val items: List<String>)

val defaultSeed = listOf(
    DefaultCategory("Toiletries", listOf("Toothbrush", "Toothpaste", "Deodorant", "Shampoo")),
    DefaultCategory("Clothes", listOf("T-Shirts", "Jeans", "Underwear", "Socks")),
    DefaultCategory("Electronics", listOf("Phone charger", "Power bank", "Headphones"))
)


