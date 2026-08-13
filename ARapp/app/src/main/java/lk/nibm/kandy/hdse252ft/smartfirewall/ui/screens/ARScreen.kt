package lk.nibm.kandy.hdse252ft.smartfirewall.ui.screens

import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.CubeNode
import kotlinx.coroutines.delay
import lk.nibm.kandy.hdse252ft.smartfirewall.data.api.ApiClient
import lk.nibm.kandy.hdse252ft.smartfirewall.data.api.NodeResponse

// ── Placed Node Data Model ────────────────────────────────────
data class PlacedNode(
    val id: String,
    val ip: String,
    val status: String,
    val position: Position,
    val sphereNode: CylinderNode
)

// ── AR Screen ─────────────────────────────────────────────────
@Composable
fun ARScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var nodesList by remember { mutableStateOf<List<NodeResponse>>(emptyList()) }
    var trackingState by remember { mutableStateOf("Initializing...") }
    var selectedNode  by remember { mutableStateOf<NodeResponse?>(null) }
    var apiError by remember { mutableStateOf(false) }

    val arSceneView = remember { mutableStateOf<ARSceneView?>(null) }

    var arFrame by remember { mutableStateOf<com.google.ar.core.Frame?>(null) }

    // Placement state for manual positioning mode
    var isManualMode by remember { mutableStateOf(false) }
    var routerPosition by remember { mutableStateOf<Position?>(null) }
    val placedNodes = remember { mutableStateListOf<PlacedNode>() }

    // Fetch live data every 5 seconds
    LaunchedEffect(Unit) {
        while (true) {
            try {
                nodesList = ApiClient.api.getNodes()
                apiError = false
            } catch (e: Exception) {
                apiError = true
            }
            delay(5000)
        }
    }

    // Active nodes used for rendering (real scanned list, or mock fallback)
    val activeNodes = if (nodesList.isNotEmpty()) nodesList else listOf(
        NodeResponse("Node-1", "192.168.1.1", "High"),
        NodeResponse("Node-2", "192.168.1.2", "Medium"),
        NodeResponse("Node-3", "192.168.1.3", "Safe")
    )

    // Helper to reset manual placements
    fun resetManualPlacement() {
        routerPosition = null
        placedNodes.clear()
        val sceneView = arSceneView.value
        sceneView?.childNodes?.toList()?.forEach { child ->
            sceneView.removeChildNode(child)
        }
    }

    // Reactively update 3D nodes in Auto-Mode
    LaunchedEffect(nodesList, arSceneView.value, isManualMode) {
        if (isManualMode) return@LaunchedEffect
        val sceneView = arSceneView.value ?: return@LaunchedEffect

        // Clear existing children nodes to prevent duplication
        sceneView.childNodes.toList().forEach { child ->
            sceneView.removeChildNode(child)
        }

        // Central Router Node position
        val routerPos = Position(0f, 0.3f, -1.8f)

        // 1. Add Router Cylinder Hub Node (Google Nest/Hub shape)
        val routerSphere = CylinderNode(
            engine = sceneView.engine,
            radius = 0.08f,
            height = 0.03f,
            materialInstance = sceneView.materialLoader.createColorInstance(
                color = io.github.sceneview.math.Color(0f, 0.6f, 1f, 1f), // Light blue router
                metallic = 0.7f,
                roughness = 0.2f
            )
        ).apply {
            position = routerPos
            isTouchable = true
            onSingleTapConfirmed = {
                Toast.makeText(context, "Smart Home Router Gateway", Toast.LENGTH_SHORT).show()
                true
            }
        }
        sceneView.addChildNode(routerSphere)

        // 2. Add client disk nodes and connection lines
        val count = activeNodes.size
        val spacing = 0.28f
        val startX = -((count - 1) * spacing) / 2f

        activeNodes.forEachIndexed { index, node ->
            val (r, g, b) = when (node.status) {
                "High"   -> Triple(1f, 0f, 0f)
                "Medium" -> Triple(1f, 0.6f, 0f)
                else     -> Triple(0f, 0.8f, 0f)
            }

            val color = io.github.sceneview.math.Color(r, g, b, 1f)
            val lineColor = io.github.sceneview.math.Color(r, g, b, 0.8f)

            // Symmetrical horizontal spread positioning
            val xPos = startX + (index * spacing)
            val clientPos = Position(xPos, 0f, -1.5f)

            // Create client flat cylinder disk node
            val clientSphere = CylinderNode(
                engine = sceneView.engine,
                radius = 0.065f,
                height = 0.02f,
                materialInstance = sceneView.materialLoader.createColorInstance(
                    color = color,
                    metallic = 0.6f,
                    roughness = 0.3f
                )
            ).apply {
                position = clientPos
                isTouchable = true
                onSingleTapConfirmed = { motionEvent ->
                    selectedNode = node
                    true
                }
            }
            sceneView.addChildNode(clientSphere)

            // Create connection line (thin cube) from clientPos to routerPos
            val dx = routerPos.x - clientPos.x
            val dy = routerPos.y - clientPos.y
            val dz = routerPos.z - clientPos.z
            val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)

            val lineNode = CubeNode(
                engine = sceneView.engine,
                size = Size(0.006f, 0.006f, length), // Thin 6mm line
                materialInstance = sceneView.materialLoader.createColorInstance(
                    color = lineColor
                )
            ).apply {
                position = Position(
                    (clientPos.x + routerPos.x) / 2f,
                    (clientPos.y + routerPos.y) / 2f,
                    (clientPos.z + routerPos.z) / 2f
                )
                lookAt(routerPos)
            }
            sceneView.addChildNode(lineNode)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── AR View ───────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                ARSceneView(ctx).apply {
                    arSceneView.value = this
                    // Configure AR session
                    configureSession { session, config ->
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    }

                    // On session updated
                    onSessionUpdated = { session, frame ->
                        arFrame = frame
                        trackingState = when (frame.camera.trackingState) {
                            TrackingState.TRACKING -> "Tracking ✅"
                            TrackingState.PAUSED   -> "Move device slowly..."
                            else                   -> "Initializing..."
                        }
                    }

                    // Configure Tap to Place in Manual Mode
                    onTouchEvent = { motionEvent: MotionEvent, hitResult: io.github.sceneview.collision.HitResult? ->
                        val frame = arFrame
                        if (isManualMode && motionEvent.action == MotionEvent.ACTION_UP && frame != null) {
                            val arHitResult = frame.hitTest(motionEvent.x, motionEvent.y).firstOrNull()
                            val hitPose = arHitResult?.hitPose
                            if (hitPose != null) {
                                val tapPos = Position(hitPose.tx(), hitPose.ty(), hitPose.tz())

                                if (routerPosition == null) {
                                    // 1. Place Router Node (Cylinder disk)
                                    routerPosition = tapPos
                                    val routerSphere = CylinderNode(
                                        engine = engine,
                                        radius = 0.08f,
                                        height = 0.03f,
                                        materialInstance = materialLoader.createColorInstance(
                                            color = io.github.sceneview.math.Color(0f, 0.6f, 1f, 1f),
                                            metallic = 0.7f,
                                            roughness = 0.2f
                                        )
                                    ).apply {
                                        position = tapPos
                                        isTouchable = true
                                        onSingleTapConfirmed = {
                                            Toast.makeText(context, "Smart Home Router Gateway", Toast.LENGTH_SHORT).show()
                                            true
                                        }
                                    }
                                    addChildNode(routerSphere)
                                } else if (placedNodes.size < activeNodes.size) {
                                    // 2. Place Client Node (Cylinder disk)
                                    val node = activeNodes[placedNodes.size]
                                    val (r, g, b) = when (node.status) {
                                        "High"   -> Triple(1f, 0f, 0f)
                                        "Medium" -> Triple(1f, 0.6f, 0f)
                                        else     -> Triple(0f, 0.8f, 0f)
                                    }
                                    val color = io.github.sceneview.math.Color(r, g, b, 1f)
                                    val lineColor = io.github.sceneview.math.Color(r, g, b, 0.8f)

                                    val clientSphere = CylinderNode(
                                        engine = engine,
                                        radius = 0.065f,
                                        height = 0.02f,
                                        materialInstance = materialLoader.createColorInstance(
                                            color = color,
                                            metallic = 0.6f,
                                            roughness = 0.3f
                                        )
                                    ).apply {
                                        position = tapPos
                                        isTouchable = true
                                        onSingleTapConfirmed = {
                                            selectedNode = node
                                            true
                                        }
                                    }
                                    addChildNode(clientSphere)

                                    // 3. Draw Connecting Line Node
                                    val rPos = routerPosition!!
                                    val dx = rPos.x - tapPos.x
                                    val dy = rPos.y - tapPos.y
                                    val dz = rPos.z - tapPos.z
                                    val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)

                                    val lineNode = CubeNode(
                                        engine = engine,
                                        size = Size(0.006f, 0.006f, length),
                                        materialInstance = materialLoader.createColorInstance(
                                            color = lineColor
                                        )
                                    ).apply {
                                        position = Position(
                                            (tapPos.x + rPos.x) / 2f,
                                            (tapPos.y + rPos.y) / 2f,
                                            (tapPos.z + rPos.z) / 2f
                                        )
                                        lookAt(rPos)
                                    }
                                    addChildNode(lineNode)

                                    placedNodes.add(PlacedNode(node.id, node.ip, node.status, tapPos, clientSphere))
                                }
                            }
                        }
                        false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Instruction Banner (Manual Mode) ──────────────────
        if (isManualMode) {
            val bannerText = when {
                routerPosition == null -> "Tap to place Router Gateway"
                placedNodes.size < activeNodes.size -> {
                    val nextNode = activeNodes[placedNodes.size]
                    "Tap to place ${nextNode.id} (${nextNode.ip})"
                }
                else -> "All nodes placed! Tap a node for details."
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp)
                    .background(Color(0xFF00C2FF).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(bannerText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // ── Top Controls Bar ──────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("← Back", color = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Mode Toggle Button
                    TextButton(
                        onClick = {
                            isManualMode = !isManualMode
                            resetManualPlacement()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (isManualMode) Color(0xFF00C2FF) else Color.Black.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isManualMode) "Manual Placement" else "Auto Placement", color = Color.White)
                    }

                    // Reset Button (Manual Mode only)
                    if (isManualMode) {
                        TextButton(
                            onClick = { resetManualPlacement() },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = Color(0xFFE63946)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reset", color = Color.White)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .align(Alignment.CenterVertically)
                    ) {
                        val statusText = if (apiError) "Offline" else trackingState
                        Text(text = statusText, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // ── Legend ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text("Network Nodes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            LegendItem(Color.Red,                 "High Threat")
            LegendItem(Color(0xFFF4A261), "Medium Threat")
            LegendItem(Color(0xFF2DC653), "Safe")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Tap a node for details", color = Color.Gray, fontSize = 11.sp)
        }

        // ── Node Detail Popup ─────────────────────────────────
        selectedNode?.let { node ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF132040).copy(alpha = 0.95f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(node.id, color = Color.White, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { selectedNode = null }) {
                                Text("✕", color = Color.Gray)
                            }
                        }
                        Text("IP: ${node.ip}",       color = Color(0xFF8899AA), fontSize = 12.sp)
                        Text("Status: ${node.status}", color = when (node.status) {
                            "High"   -> Color(0xFFE63946)
                            "Medium" -> Color(0xFFF4A261)
                            else     -> Color(0xFF2DC653)
                        }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Blockchain: Connected", color = Color(0xFF2DC653), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ── Legend Item ───────────────────────────────────────────────
@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(5.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}
