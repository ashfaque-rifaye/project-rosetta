package app.rosetta.tv.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import com.google.android.filament.Engine
import io.github.sceneview.SceneView
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberNode
import kotlin.math.sin

/**
 * A real-time 3D model (Google Filament via SceneView) that turns slowly and bobs: the gold star a child earns,
 * the treasure chest of the wish list. Models are glTF files in assets/models (made by assets/models/make_models.py).
 */
/**
 * One Filament engine and loaders for the whole app, never torn down. Each 3D view still creates and destroys its own
 * view, scene and model; destroying a loader after its engine crashed gltfio on 32-bit Fire OS when leaving a screen.
 */
private class Shared3D private constructor(context: Context) {
    val engine: Engine = SceneView.createEngine(SceneView.createEglContext())
    val modelLoader: ModelLoader = SceneView.createModelLoader(engine, context)
    val materialLoader: MaterialLoader = SceneView.createMaterialLoader(engine, context)
    val environmentLoader: EnvironmentLoader = SceneView.createEnvironmentLoader(engine, context)

    companion object {
        @Volatile private var instance: Shared3D? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: Shared3D(context.applicationContext).also { instance = it } }
    }
}

@Composable
fun Model3D(asset: String, modifier: Modifier = Modifier, units: Float = 1.7f, spinDegPerSec: Float = 50f, tilt: Float = 10f) {
    val context = LocalContext.current
    val shared = remember { Shared3D.get(context) }
    val engine = shared.engine
    val modelLoader = shared.modelLoader
    val cameraNode = rememberCameraNode(engine) { position = Position(z = 3.4f) }
    // rememberNode destroys the model before the loader and engine go away (leaving the screen used to crash gltfio).
    val node = rememberNode {
        ModelNode(modelInstance = modelLoader.createModelInstance(asset), scaleToUnits = units, centerOrigin = Position(0f, 0f, 0f))
    }
    var start by remember { mutableLongStateOf(0L) }
    Scene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = shared.materialLoader,
        environmentLoader = shared.environmentLoader,
        cameraNode = cameraNode,
        childNodes = listOf(node),
        isOpaque = false,
        cameraManipulator = null,
        onFrame = { t ->
            if (start == 0L) start = t
            val s = (t - start) / 1_000_000_000f
            node.rotation = Rotation(x = tilt + 5f * sin(s * 1.3f), y = s * spinDegPerSec) // seen a little from above, gently rocking
            node.position = Position(y = 0.06f * sin(s * 2.1f))
        },
    )
}
