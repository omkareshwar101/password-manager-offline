package com.offlinevault.app.ui.addedit

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.offlinevault.app.R
import com.offlinevault.app.data.model.AccountCategory
import com.offlinevault.app.di.AppContainer
import com.offlinevault.app.ui.components.AccountAvatar
import com.offlinevault.app.ui.components.BuiltInIconPickerDialog
import com.offlinevault.app.ui.components.PasswordGeneratorDialog
import com.offlinevault.app.ui.components.PasswordStrengthMeter
import com.offlinevault.app.viewmodel.AddEditViewModel
import com.offlinevault.app.ui.viewmodel.appViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAccountScreen(
    accountId: Long?,
    container: AppContainer,
    navController: NavHostController,
) {
    val viewModel = appViewModel(key = if (accountId == null) "add-account" else "edit-$accountId") {
        AddEditViewModel(accountId, it.accountRepository, it.imageStore)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showPhotoOptions by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val imageErrorText = stringResource(R.string.image_pick_failed)
    val cameraErrorText = stringResource(R.string.camera_unavailable)
    val saveErrorText = stringResource(R.string.error_generic)
    val savedText = stringResource(R.string.account_saved)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(viewModel::importImage)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = cameraFile
        val uri = cameraUri
        cameraFile = null
        cameraUri = null
        if (success && uri != null) {
            viewModel.importImage(uri)
        } else {
            file?.delete()
        }
    }

    fun launchCamera() {
        runCatching {
            val file = container.imageStore.createCameraTempFile()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            cameraFile = file
            cameraUri = uri
            cameraLauncher.launch(uri)
        }.onFailure {
            cameraFile?.delete()
            cameraFile = null
            cameraUri = null
            scope.launch { snackbarHostState.showSnackbar(cameraErrorText) }
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            Toast.makeText(context, savedText, Toast.LENGTH_SHORT).show()
            navController.popBackStack()
        }
    }

    LaunchedEffect(state.imageError) {
        if (state.imageError) {
            snackbarHostState.showSnackbar(imageErrorText)
            viewModel.clearImageError()
        }
    }

    LaunchedEffect(state.saveError) {
        if (state.saveError) {
            snackbarHostState.showSnackbar(saveErrorText)
            viewModel.clearSaveError()
        }
    }

    if (showPhotoOptions) {
        PhotoOptionsDialog(
            hasImage = state.imageRef != null,
            onDismiss = { showPhotoOptions = false },
            onGallery = {
                showPhotoOptions = false
                galleryLauncher.launch("image/*")
            },
            onCamera = {
                showPhotoOptions = false
                launchCamera()
            },
            onIcon = {
                showPhotoOptions = false
                showIconPicker = true
            },
            onRemove = {
                showPhotoOptions = false
                viewModel.removeImage()
            },
        )
    }

    if (showIconPicker) {
        BuiltInIconPickerDialog(
            onSelect = { ref ->
                viewModel.selectBuiltInIcon(ref)
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false },
        )
    }

    if (showGenerator) {
        PasswordGeneratorDialog(
            container = container,
            onDismiss = { showGenerator = false },
            onUsePassword = viewModel::setPassword,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (accountId == null) R.string.new_account else R.string.edit_account_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { container.sessionManager.lock() }) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = stringResource(R.string.content_desc_lock),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving && !state.isLoading && !state.loadFailed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.saving))
                    } else {
                        Text(
                            stringResource(
                                if (accountId == null) R.string.save else R.string.save_changes,
                            ),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.loadFailed -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.account_not_found))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { navController.popBackStack() }) {
                            Text(stringResource(R.string.back))
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ImageEditor(
                        imageRef = state.imageRef,
                        accountName = state.name.ifBlank { stringResource(R.string.new_account) },
                        imageBusy = state.imageBusy,
                        onClick = { showPhotoOptions = true },
                    )

                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::setName,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.account_name)) },
                        placeholder = { Text(stringResource(R.string.account_name_hint)) },
                        singleLine = true,
                        isError = state.nameError,
                        supportingText = if (state.nameError) {
                            { Text(stringResource(R.string.name_required)) }
                        } else {
                            null
                        },
                        enabled = !state.isSaving,
                    )

                    OutlinedTextField(
                        value = state.url,
                        onValueChange = viewModel::setUrl,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.url_optional)) },
                        placeholder = { Text(stringResource(R.string.url_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        isError = state.urlError,
                        supportingText = {
                            Text(
                                stringResource(
                                    if (state.urlError) R.string.url_invalid else R.string.link_note,
                                ),
                            )
                        },
                        enabled = !state.isSaving,
                    )

                    OutlinedTextField(
                        value = state.username,
                        onValueChange = viewModel::setUsername,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.username_optional)) },
                        placeholder = { Text(stringResource(R.string.username_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !state.isSaving,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = viewModel::setPassword,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.password)) },
                            placeholder = { Text(stringResource(R.string.password_hint)) },
                            singleLine = true,
                            isError = state.passwordError,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) {
                                                Icons.Outlined.VisibilityOff
                                            } else {
                                                Icons.Outlined.Visibility
                                            },
                                            contentDescription = stringResource(
                                                if (passwordVisible) {
                                                    R.string.hide_password
                                                } else {
                                                    R.string.reveal_password
                                                },
                                            ),
                                        )
                                    }
                                    IconButton(onClick = { showGenerator = true }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Key,
                                            contentDescription = stringResource(R.string.generate_password),
                                        )
                                    }
                                }
                            },
                            supportingText = if (state.passwordError) {
                                { Text(stringResource(R.string.password_required)) }
                            } else {
                                null
                            },
                            enabled = !state.isSaving,
                        )
                        if (state.password.isNotEmpty()) {
                            PasswordStrengthMeter(
                                password = state.password,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        TextButton(
                            onClick = { showGenerator = true },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.generate_password))
                        }
                    }

                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = viewModel::setNotes,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.notes_optional)) },
                        placeholder = { Text(stringResource(R.string.notes_hint)) },
                        minLines = 3,
                        maxLines = 7,
                        enabled = !state.isSaving,
                    )

                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = {
                            if (!state.isSaving) categoryExpanded = !categoryExpanded
                        },
                    ) {
                        OutlinedTextField(
                            value = stringResource(state.category.labelRes),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.category)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Category, contentDescription = null)
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            enabled = !state.isSaving,
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                        ) {
                            AccountCategory.entries.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(category.labelRes)) },
                                    onClick = {
                                        viewModel.setCategory(category)
                                        categoryExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.detail_security_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ImageEditor(
    imageRef: String?,
    accountName: String,
    imageBusy: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !imageBusy, onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AccountAvatar(
                    imageRef = imageRef,
                    accountName = accountName,
                    size = 76.dp,
                    shape = CircleShape,
                )
                if (imageBusy) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddAPhoto,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.photo_or_icon),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.choose_photo_or_icon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhotoOptionsDialog(
    hasImage: Boolean,
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onIcon: () -> Unit,
    onRemove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_image)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PhotoOption(
                    icon = Icons.Outlined.PhotoLibrary,
                    label = stringResource(R.string.pick_from_gallery),
                    onClick = onGallery,
                )
                PhotoOption(
                    icon = Icons.Outlined.PhotoCamera,
                    label = stringResource(R.string.take_photo),
                    onClick = onCamera,
                )
                PhotoOption(
                    icon = Icons.Outlined.EmojiEmotions,
                    label = stringResource(R.string.built_in_icons),
                    onClick = onIcon,
                )
                if (hasImage) {
                    PhotoOption(
                        icon = Icons.Outlined.Delete,
                        label = stringResource(R.string.remove_image),
                        onClick = onRemove,
                        destructive = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PhotoOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = label,
                color = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
