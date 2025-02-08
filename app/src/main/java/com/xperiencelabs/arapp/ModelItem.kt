package com.xperiencelabs.arapp

data class ModelItem(
    val name: String,
    val modelPath: String,      // Path to your .glb file (e.g., "models/sofa.glb")
    val thumbnailRes: Int       // Drawable resource for thumbnail
)
