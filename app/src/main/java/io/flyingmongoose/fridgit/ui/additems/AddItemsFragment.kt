package io.flyingmongoose.fridgit.ui.additems

import android.Manifest
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.internal.Objects
import com.google.android.material.chip.Chip
import io.flyingmongoose.fridgit.R
import com.livinglifetechway.quickpermissions_kotlin.runWithPermissions
import com.livinglifetechway.quickpermissions_kotlin.util.QuickPermissionsOptions
import com.livinglifetechway.quickpermissions_kotlin.util.QuickPermissionsRequest
import io.flyingmongoose.fridgit.mlkit.LiveBarcodeScanningActivity
import io.flyingmongoose.fridgit.mlkit.barcodedetection.BarcodeField
import io.flyingmongoose.fridgit.mlkit.barcodedetection.BarcodeProcessor
import io.flyingmongoose.fridgit.mlkit.barcodedetection.BarcodeResultFragment
import io.flyingmongoose.fridgit.mlkit.camera.CameraSource
import io.flyingmongoose.fridgit.mlkit.camera.CameraSourcePreview
import io.flyingmongoose.fridgit.mlkit.camera.GraphicOverlay
import io.flyingmongoose.fridgit.mlkit.camera.WorkflowModel
import java.io.IOException
import java.util.ArrayList

class AddItemsFragment : Fragment()
{
    companion object {
        private const val TAG = "LiveBarcodeFragment"
    }

    private lateinit var addItemsViewModel: AddItemsViewModel
    private val quickPermissionsOption = QuickPermissionsOptions(
        rationaleMessage = "Custom rational message",
        permanentlyDeniedMessage = "Custom permanently denied message",
        rationaleMethod = { rationaleCallback(it) },
        permanentDeniedMethod = { permissionsPermanentlyDenied(it) },
        permissionsDeniedMethod = { whenPermAreDenied(it) }
    )

    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var settingsButton: View? = null
    private var flashButton: View? = null
    private var promptChip: Chip? = null
    private var promptChipAnimator: AnimatorSet? = null
    private var workflowModel: WorkflowModel? = null
    private var currentWorkflowState: WorkflowModel.WorkflowState? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View?
    {
        addItemsViewModel =
                ViewModelProvider(this).get(AddItemsViewModel::class.java)

        val root = inflater.inflate(R.layout.activity_live_barcode, container, false)

        preview = root.findViewById(R.id.camera_preview)
        graphicOverlay = root.findViewById<GraphicOverlay>(R.id.camera_preview_graphic_overlay).apply {
            setOnClickListener{onOverlayClicked()}
            cameraSource = CameraSource(this)
        }

        promptChip = root.findViewById(R.id.bottom_prompt_chip)
        promptChipAnimator =
            (AnimatorInflater.loadAnimator(requireContext(), R.animator.bottom_prompt_chip_enter) as AnimatorSet).apply {
                setTarget(promptChip)
            }

        flashButton = root.findViewById<View>(R.id.flash_button).apply {
            setOnClickListener{onFlashClicked(this)}
        }

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        runWithPermissions(Manifest.permission.CAMERA, options = quickPermissionsOption) {
            setUpWorkflowModel()
        }
    }

    override fun onResume() {
        super.onResume()

        workflowModel?.markCameraFrozen()
        settingsButton?.isEnabled = true
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        cameraSource?.setFrameProcessor(BarcodeProcessor(graphicOverlay!!, workflowModel!!))
        workflowModel?.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        cameraSource = null
    }

    fun onFlashClicked(view: View) {
        flashButton?.let {
            if (it.isSelected) {
                it.isSelected = false
                cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
            } else {
                it.isSelected = true
                cameraSource!!.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
            }
        }
    }

    fun onOverlayClicked() {
        BarcodeResultFragment.dismiss(parentFragmentManager)
    }

    private fun startCameraPreview() {
        val workflowModel = this.workflowModel ?: return
        val cameraSource = this.cameraSource ?: return
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview?.start(cameraSource)
            } catch (e: IOException) {
                Log.e(AddItemsFragment.TAG, "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        val workflowModel = this.workflowModel ?: return
        if (workflowModel.isCameraLive) {
            workflowModel.markCameraFrozen()
            flashButton?.isSelected = false
            preview?.stop()
        }
    }

    private fun setUpWorkflowModel() {
        workflowModel = ViewModelProvider(requireActivity()).get(WorkflowModel::class.java)

        // Observes the workflow state changes, if happens, update the overlay view indicators and
        // camera preview state.
        workflowModel!!.workflowState.observe(requireActivity(), Observer { workflowState ->
            if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                return@Observer
            }

            currentWorkflowState = workflowState
            Log.d(AddItemsFragment.TAG, "Current workflow state: ${currentWorkflowState!!.name}")

            val wasPromptChipGone = promptChip?.visibility == View.GONE

            when (workflowState) {
                WorkflowModel.WorkflowState.DETECTING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.setText(R.string.prompt_point_at_a_barcode)
                    startCameraPreview()
                }
                WorkflowModel.WorkflowState.CONFIRMING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.setText(R.string.prompt_move_camera_closer)
                    startCameraPreview()
                }
                WorkflowModel.WorkflowState.SEARCHING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.setText(R.string.prompt_searching)
                    stopCameraPreview()
                }
                WorkflowModel.WorkflowState.DETECTED, WorkflowModel.WorkflowState.SEARCHED -> {
                    promptChip?.visibility = View.GONE
                    stopCameraPreview()
                }
                else -> promptChip?.visibility = View.GONE
            }

            val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
            promptChipAnimator?.let {
                if (shouldPlayPromptChipEnteringAnimation && !it.isRunning) it.start()
            }
        })

        workflowModel?.detectedBarcode?.observe(viewLifecycleOwner, Observer { barcode ->
            if (barcode != null) {
                val barcodeFieldList = ArrayList<BarcodeField>()
                barcodeFieldList.add(BarcodeField("Raw Value", barcode.rawValue ?: ""))
                BarcodeResultFragment.show(parentFragmentManager, barcodeFieldList)
            }
        })
    }

    private fun rationaleCallback(req: QuickPermissionsRequest) {
        // this will be called when permission is denied once or more time. Handle it your way
        AlertDialog.Builder(requireContext())
            .setTitle("Permissions Denied")
            .setMessage("In order to add items to your fridge, we need access to your camera to scan item barcodes")
            .setPositiveButton("Ask Again") { _, _ ->
                req.proceed()
            }
            .setNegativeButton("Nope") {_, _ ->
                findNavController().navigateUp()
            }
            .setCancelable(false)
            .show()
    }

    private fun permissionsPermanentlyDenied(req: QuickPermissionsRequest) {
        // this will be called when some/all permissions required by the method are permanently
        // denied. Handle it your way.
        AlertDialog.Builder(requireContext())
            .setTitle("Permissions Permanently Denied")
            .setMessage("This app cannot function correctly without permissions to your camera to scan item barcodes.\n" +
                    "Please enable camera permission for Fridgit from you device settings")
            .setPositiveButton("App Settings") { dialog, which -> req.openAppSettings() }
            .setNegativeButton("Cancel") { dialog, which ->
                req.cancel()
                findNavController().navigateUp()
            }
            .setCancelable(false)
            .show()
    }

    private fun whenPermAreDenied(req: QuickPermissionsRequest) {
        // handle something when permissions are not granted and the request method cannot be called
        AlertDialog.Builder(requireContext())
            .setTitle("Permissions Denied")
            .setMessage("In order to add items to your fridge, we need access to your camera to scan item barcodes")
            .setPositiveButton("Ask Again") { _, _ ->
                req.proceed()
            }
            .setNegativeButton("Nope") {_, _ ->
                findNavController().navigateUp()
            }
            .setCancelable(false)
            .show()
    }

}