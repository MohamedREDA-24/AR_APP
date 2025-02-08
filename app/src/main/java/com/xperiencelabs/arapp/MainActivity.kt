package com.xperiencelabs.arapp

import ModelAdapter
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isGone
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.ar.core.Config
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation

class MainActivity : AppCompatActivity() {

    private lateinit var sceneView: ArSceneView
    private lateinit var placeButton: ExtendedFloatingActionButton
    private lateinit var modelNode: ArModelNode
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var rotateGestureDetector: GestureDetector

    private var currentRotation = 0f  // Track model rotation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup the DrawerLayout
        drawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.openDrawer(GravityCompat.START)

        // Setup AR SceneView
        sceneView = findViewById<ArSceneView>(R.id.sceneView).apply {
            this.lightEstimationMode = Config.LightEstimationMode.DISABLED
        }

        placeButton = findViewById(R.id.place)
        placeButton.setOnClickListener {
            placeModel()
        }

        val openSidebarButton = findViewById<Button>(R.id.open_sidebar_button)
        openSidebarButton.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START) // Close if open
            } else {
                drawerLayout.openDrawer(GravityCompat.START) // Open if closed
            }
        }

        // Setup the sidebar (RecyclerView)
        val modelListRecyclerView = findViewById<RecyclerView>(R.id.model_list)
        modelListRecyclerView.layoutManager = LinearLayoutManager(this)

        // Sample list of models
        val models = listOf(
            ModelItem("Sofa", "models/sofa.glb", R.drawable.ic_placeholder),
            ModelItem("Chair2", "models/chaise.glb", R.drawable.ic_placeholder),
            ModelItem("Chair", "models/chair.glb", R.drawable.ic_placeholder),
            ModelItem("Table", "models/table.glb", R.drawable.ic_placeholder)
        )

        val adapter = ModelAdapter(models) { modelItem ->
            loadModel(modelItem)
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        modelListRecyclerView.adapter = adapter
    }

    /**
     * Loads a selected model into the AR scene.
     */
    private fun loadModel(modelItem: ModelItem) {
        // Ensure we remove the previous model before adding a new one
        if (::modelNode.isInitialized) {
            sceneView.removeChild(modelNode)
        }

        modelNode = ArModelNode(sceneView.engine, PlacementMode.INSTANT).apply {
            loadModelGlbAsync(
                glbFileLocation = modelItem.modelPath,
                scaleToUnits = 1f,  // Default scale
                centerOrigin = Position(-0.5f)
            ) {
                sceneView.planeRenderer.isVisible = true
            }

            onAnchorChanged = {
                placeButton.isGone = false
            }
        }

        sceneView.addChild(modelNode)

        // 🔹 Always show the button when a new model is loaded
        placeButton.isGone = false

        // 🔹 Setup Gestures (Scaling + Rotating)
        setupGestureControls()
    }

    /**
     * Anchors the current model.
     */
    private fun placeModel() {
        modelNode.anchor()
        sceneView.planeRenderer.isVisible = false
    }

    /**
     * Handles pinch-to-zoom (scaling) and drag-to-rotate gestures.
     */
    private fun setupGestureControls() {
        // Pinch to Scale Gesture
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (::modelNode.isInitialized) {
                    val newScale = modelNode.scale.x * detector.scaleFactor
                    val clampedScale = newScale.coerceIn(0.5f, 2.0f)
                    modelNode.scale = Position(clampedScale, clampedScale, clampedScale)
                }
                return true
            }
        })

        // Drag to Rotate Gesture
        rotateGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (::modelNode.isInitialized) {
                    currentRotation -= distanceX * 0.5f  // Adjust sensitivity
                    modelNode.rotation = Rotation(y = currentRotation)
                }
                return true
            }
        })

        // Attach both gesture detectors to the SceneView
        sceneView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            rotateGestureDetector.onTouchEvent(event)
        }
    }
}
