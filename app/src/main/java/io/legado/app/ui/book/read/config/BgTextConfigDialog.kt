package io.legado.app.ui.book.read.config

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogReadBgTextBinding
import io.legado.app.databinding.ItemBgImageBinding
import io.legado.app.help.ReadBookConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.document.FilePicker
import io.legado.app.ui.document.FilePickerParam
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding
import rxhttp.wrapper.param.RxHttp
import rxhttp.wrapper.param.toByteArray
import java.io.File

class BgTextConfigDialog : BaseDialogFragment() {

    companion object {
        const val TEXT_COLOR = 121
        const val BG_COLOR = 122
    }

    private val binding by viewBinding(DialogReadBgTextBinding::bind)
    private val configFileName = "readConfig.zip"
    private lateinit var adapter: BgAdapter
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private val importFormNet = "网络导入"
    private val selectBgImage = registerForActivityResult(ActivityResultContracts.GetContent()) {
        setBgFromUri(it)
    }
    private val selectExportDir = registerForActivityResult(FilePicker()) {
        it?.let {
            exportConfig(it)
        }
    }
    private val selectImportDoc = registerForActivityResult(FilePicker()) {
        it ?: return@registerForActivityResult
        if (it.toString() == importFormNet) {
            importNetConfigAlert()
        } else {
            importConfig(it)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let {
            it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            it.setBackgroundDrawableResource(R.color.background)
            it.decorView.setPadding(0, 0, 0, 0)
            val attr = it.attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            it.attributes = attr
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        (activity as ReadBookActivity).bottomDialog++
        return inflater.inflate(R.layout.dialog_read_bg_text, container)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initData()
        initEvent()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    private fun initView() {
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        primaryTextColor = requireContext().getPrimaryTextColor(isLight)
        secondaryTextColor = requireContext().getSecondaryTextColor(isLight)
        binding.rootView.setBackgroundColor(bg)
        binding.swDarkStatusIcon.setTextColor(primaryTextColor)
        binding.ivImport.setColorFilter(primaryTextColor)
        binding.ivExport.setColorFilter(primaryTextColor)
        binding.ivDelete.setColorFilter(primaryTextColor)
        binding.tvBgImage.setTextColor(primaryTextColor)
    }

    @SuppressLint("InflateParams")
    private fun initData() = with(ReadBookConfig.durConfig) {
        binding.tvName.text = name.ifBlank { "文字" }
        binding.swDarkStatusIcon.isChecked = curStatusIconDark()
        adapter = BgAdapter(requireContext(), secondaryTextColor)
        binding.recyclerView.adapter = adapter
        adapter.addHeaderView {
            ItemBgImageBinding.inflate(layoutInflater, it, false).apply {
                tvName.setTextColor(secondaryTextColor)
                tvName.text = getString(R.string.select_image)
                ivBg.setImageResource(R.drawable.ic_image)
                ivBg.setColorFilter(primaryTextColor)
                root.setOnClickListener {
                    selectBgImage.launch("image/*")
                }
            }
        }
        requireContext().assets.list("bg")?.let {
            adapter.setItems(it.toList())
        }
    }

    @SuppressLint("InflateParams")
    private fun initEvent() = with(ReadBookConfig.durConfig) {
        binding.ivEdit.setOnClickListener {
            alert(R.string.style_name) {
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.setText(ReadBookConfig.durConfig.name)
                }
                customView { alertBinding.root }
                okButton {
                    alertBinding.editView.text?.toString()?.let {
                        binding.tvName.text = it
                        ReadBookConfig.durConfig.name = it
                    }
                }
                cancelButton()
            }.show()
        }
        binding.swDarkStatusIcon.setOnCheckedChangeListener { _, isChecked ->
            setCurStatusIconDark(isChecked)
            (activity as? ReadBookActivity)?.upSystemUiVisibility()
        }
        binding.tvTextColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(curTextColor())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(TEXT_COLOR)
                .show(requireActivity())
        }
        binding.tvBgColor.setOnClickListener {
            val bgColor =
                if (curBgType() == 0) Color.parseColor(curBgStr())
                else Color.parseColor("#015A86")
            ColorPickerDialog.newBuilder()
                .setColor(bgColor)
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(BG_COLOR)
                .show(requireActivity())
        }
        binding.ivImport.setOnClickListener {
            selectImportDoc.launch(
                FilePickerParam(
                    mode = FilePicker.FILE,
                    title = getString(R.string.import_str),
                    allowExtensions = arrayOf("zip"),
                    otherActions = arrayOf(importFormNet)
                )
            )
        }
        binding.ivExport.setOnClickListener {
            selectExportDir.launch(
                FilePickerParam(
                    title = getString(R.string.export_str)
                )
            )
        }
        binding.ivDelete.setOnClickListener {
            if (ReadBookConfig.deleteDur()) {
                postEvent(EventBus.UP_CONFIG, true)
                dismissAllowingStateLoss()
            } else {
                toastOnUi("数量已是最少,不能删除.")
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun exportConfig(uri: Uri) {
        val exportFileName = if (ReadBookConfig.config.name.isBlank()) {
            configFileName
        } else {
            "${ReadBookConfig.config.name}.zip"
        }
        execute {
            val exportFiles = arrayListOf<File>()
            val configDirPath = FileUtils.getPath(requireContext().eCacheDir, "readConfig")
            FileUtils.deleteFile(configDirPath)
            val configDir = FileUtils.createFolderIfNotExist(configDirPath)
            val configExportPath = FileUtils.getPath(configDir, "readConfig.json")
            FileUtils.deleteFile(configExportPath)
            val configExportFile = FileUtils.createFileIfNotExist(configExportPath)
            configExportFile.writeText(GSON.toJson(ReadBookConfig.getExportConfig()))
            exportFiles.add(configExportFile)
            val fontPath = ReadBookConfig.textFont
            if (fontPath.isNotEmpty()) {
                val fontName = FileUtils.getName(fontPath)
                val fontBytes = fontPath.parseToUri().readBytes(requireContext())
                fontBytes?.let {
                    val fontExportFile = FileUtils.createFileIfNotExist(configDir, fontName)
                    fontExportFile.writeBytes(it)
                    exportFiles.add(fontExportFile)
                }
            }
            if (ReadBookConfig.durConfig.bgType == 2) {
                val bgName = FileUtils.getName(ReadBookConfig.durConfig.bgStr)
                val bgFile = File(ReadBookConfig.durConfig.bgStr)
                if (bgFile.exists()) {
                    val bgExportFile = File(FileUtils.getPath(configDir, bgName))
                    bgFile.copyTo(bgExportFile)
                    exportFiles.add(bgExportFile)
                }
            }
            if (ReadBookConfig.durConfig.bgTypeNight == 2) {
                val bgName = FileUtils.getName(ReadBookConfig.durConfig.bgStrNight)
                val bgFile = File(ReadBookConfig.durConfig.bgStrNight)
                if (bgFile.exists()) {
                    val bgExportFile = File(FileUtils.getPath(configDir, bgName))
                    bgFile.copyTo(bgExportFile)
                    exportFiles.add(bgExportFile)
                }
            }
            if (ReadBookConfig.durConfig.bgTypeEInk == 2) {
                val bgName = FileUtils.getName(ReadBookConfig.durConfig.bgStrEInk)
                val bgFile = File(ReadBookConfig.durConfig.bgStrEInk)
                if (bgFile.exists()) {
                    val bgExportFile = File(FileUtils.getPath(configDir, bgName))
                    bgFile.copyTo(bgExportFile)
                    exportFiles.add(bgExportFile)
                }
            }
            val configZipPath = FileUtils.getPath(requireContext().eCacheDir, configFileName)
            if (ZipUtils.zipFiles(exportFiles, File(configZipPath))) {
                if (uri.isContentScheme()) {
                    DocumentFile.fromTreeUri(requireContext(), uri)?.let { treeDoc ->
                        treeDoc.findFile(exportFileName)?.delete()
                        treeDoc.createFile("", exportFileName)
                            ?.writeBytes(requireContext(), File(configZipPath).readBytes())
                    }
                } else {
                    val exportPath = FileUtils.getPath(File(uri.path!!), exportFileName)
                    FileUtils.deleteFile(exportPath)
                    FileUtils.createFileIfNotExist(exportPath)
                        .writeBytes(File(configZipPath).readBytes())
                }
            }
        }.onSuccess {
            toastOnUi("导出成功, 文件名为 $exportFileName")
        }.onError {
            it.printStackTrace()
            longToast("导出失败:${it.localizedMessage}")
        }
    }

    @SuppressLint("InflateParams")
    private fun importNetConfigAlert() {
        alert("输入地址") {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater)
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { url ->
                    importNetConfig(url)
                }
            }
            noButton()
        }.show()
    }

    private fun importNetConfig(url: String) {
        execute {
            RxHttp.get(url).toByteArray().await().let {
                importConfig(it)
            }
        }.onError {
            longToast(it.msg)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun importConfig(uri: Uri) {
        execute {
            importConfig(uri.readBytes(requireContext())!!)
        }.onError {
            it.printStackTrace()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun importConfig(byteArray: ByteArray) {
        execute {
            val configZipPath = FileUtils.getPath(requireContext().eCacheDir, configFileName)
            FileUtils.deleteFile(configZipPath)
            val zipFile = FileUtils.createFileIfNotExist(configZipPath)
            zipFile.writeBytes(byteArray)
            val configDirPath = FileUtils.getPath(requireContext().eCacheDir, "readConfig")
            FileUtils.deleteFile(configDirPath)
            ZipUtils.unzipFile(zipFile, FileUtils.createFolderIfNotExist(configDirPath))
            val configDir = FileUtils.createFolderIfNotExist(configDirPath)
            val configFile = FileUtils.getFile(configDir, "readConfig.json")
            val config: ReadBookConfig.Config = GSON.fromJsonObject(configFile.readText())!!
            if (config.textFont.isNotEmpty()) {
                val fontName = FileUtils.getName(config.textFont)
                val fontPath =
                    FileUtils.getPath(requireContext().externalFilesDir, "font", fontName)
                if (!FileUtils.exist(fontPath)) {
                    FileUtils.getFile(configDir, fontName).copyTo(File(fontPath))
                }
                config.textFont = fontPath
            }
            if (config.bgType == 2) {
                val bgName = FileUtils.getName(config.bgStr)
                val bgPath = FileUtils.getPath(requireContext().externalFilesDir, "bg", bgName)
                if (!FileUtils.exist(bgPath)) {
                    val bgFile = FileUtils.getFile(configDir, bgName)
                    if (bgFile.exists()) {
                        bgFile.copyTo(File(bgPath))
                    }
                }
            }
            if (config.bgTypeNight == 2) {
                val bgName = FileUtils.getName(config.bgStrNight)
                val bgPath = FileUtils.getPath(requireContext().externalFilesDir, "bg", bgName)
                if (!FileUtils.exist(bgPath)) {
                    val bgFile = FileUtils.getFile(configDir, bgName)
                    if (bgFile.exists()) {
                        bgFile.copyTo(File(bgPath))
                    }
                }
            }
            if (config.bgTypeEInk == 2) {
                val bgName = FileUtils.getName(config.bgStrEInk)
                val bgPath = FileUtils.getPath(requireContext().externalFilesDir, "bg", bgName)
                if (!FileUtils.exist(bgPath)) {
                    val bgFile = FileUtils.getFile(configDir, bgName)
                    if (bgFile.exists()) {
                        bgFile.copyTo(File(bgPath))
                    }
                }
            }
            ReadBookConfig.durConfig = config
            postEvent(EventBus.UP_CONFIG, true)
        }.onSuccess {
            toastOnUi("导入成功")
        }.onError {
            it.printStackTrace()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun setBgFromUri(uri: Uri) {
        if (uri.toString().isContentScheme()) {
            val doc = DocumentFile.fromSingleUri(requireContext(), uri)
            doc?.name?.let {
                val file =
                    FileUtils.createFileIfNotExist(requireContext().externalFilesDir, "bg", it)
                kotlin.runCatching {
                    DocumentUtils.readBytes(requireContext(), doc.uri)
                }.getOrNull()?.let { byteArray ->
                    file.writeBytes(byteArray)
                    ReadBookConfig.durConfig.setCurBg(2, file.absolutePath)
                    ReadBookConfig.upBg()
                    postEvent(EventBus.UP_CONFIG, false)
                } ?: toastOnUi("获取文件出错")
            }
        } else {
            PermissionsCompat.Builder(this)
                .addPermissions(
                    Permissions.READ_EXTERNAL_STORAGE,
                    Permissions.WRITE_EXTERNAL_STORAGE
                )
                .rationale(R.string.bg_image_per)
                .onGranted {
                    RealPathUtil.getPath(requireContext(), uri)?.let { path ->
                        ReadBookConfig.durConfig.setCurBg(2, path)
                        ReadBookConfig.upBg()
                        postEvent(EventBus.UP_CONFIG, false)
                    }
                }
                .request()
        }
    }
}