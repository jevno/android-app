package one.mixin.android.ui.conversation

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.view.children
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tbruyelle.rxpermissions2.RxPermissions
import com.uber.autodispose.autoDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.dialog_delete.view.*
import kotlinx.android.synthetic.main.fragment_conversation.*
import kotlinx.android.synthetic.main.view_chat_control.view.*
import kotlinx.android.synthetic.main.view_reply.view.*
import kotlinx.android.synthetic.main.view_title.view.*
import kotlinx.android.synthetic.main.view_tool.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants
import one.mixin.android.Constants.Account.PREF_RECENT_USED_BOTS
import one.mixin.android.Constants.PAGE_SIZE
import one.mixin.android.Constants.RECENT_USED_BOTS_MAX_COUNT
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.RxBus
import one.mixin.android.api.request.RelationshipAction
import one.mixin.android.api.request.RelationshipRequest
import one.mixin.android.api.request.StickerAddRequest
import one.mixin.android.event.BlinkEvent
import one.mixin.android.event.DragReleaseEvent
import one.mixin.android.event.GroupEvent
import one.mixin.android.event.RecallEvent
import one.mixin.android.extension.REQUEST_CAMERA
import one.mixin.android.extension.REQUEST_FILE
import one.mixin.android.extension.REQUEST_GALLERY
import one.mixin.android.extension.addFragment
import one.mixin.android.extension.animateHeight
import one.mixin.android.extension.createImageTemp
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.deserialize
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.fadeIn
import one.mixin.android.extension.fadeOut
import one.mixin.android.extension.getAttachment
import one.mixin.android.extension.getClipboardManager
import one.mixin.android.extension.getFilePath
import one.mixin.android.extension.getImagePath
import one.mixin.android.extension.getMimeType
import one.mixin.android.extension.getUriForFile
import one.mixin.android.extension.hideKeyboard
import one.mixin.android.extension.inTransaction
import one.mixin.android.extension.isImageSupport
import one.mixin.android.extension.isUUID
import one.mixin.android.extension.lateOneHours
import one.mixin.android.extension.mainThreadDelayed
import one.mixin.android.extension.openCamera
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.extension.openUrl
import one.mixin.android.extension.putBoolean
import one.mixin.android.extension.putString
import one.mixin.android.extension.removeEnd
import one.mixin.android.extension.replaceFragment
import one.mixin.android.extension.screenHeight
import one.mixin.android.extension.selectDocument
import one.mixin.android.extension.sharedPreferences
import one.mixin.android.extension.showKeyboard
import one.mixin.android.extension.toast
import one.mixin.android.extension.translationY
import one.mixin.android.job.RefreshConversationJob
import one.mixin.android.media.OpusAudioRecorder
import one.mixin.android.media.OpusAudioRecorder.Companion.STATE_NOT_INIT
import one.mixin.android.media.OpusAudioRecorder.Companion.STATE_RECORDING
import one.mixin.android.ui.call.CallActivity
import one.mixin.android.ui.common.GroupBottomSheetDialogFragment
import one.mixin.android.ui.common.LinkFragment
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.contacts.ProfileFragment
import one.mixin.android.ui.conversation.adapter.ConversationAdapter
import one.mixin.android.ui.conversation.adapter.GalleryCallback
import one.mixin.android.ui.conversation.adapter.MentionAdapter
import one.mixin.android.ui.conversation.adapter.MentionAdapter.OnUserClickListener
import one.mixin.android.ui.conversation.adapter.Menu
import one.mixin.android.ui.conversation.adapter.MenuType
import one.mixin.android.ui.conversation.holder.BaseViewHolder
import one.mixin.android.ui.conversation.media.DragMediaActivity
import one.mixin.android.ui.conversation.preview.PreviewDialogFragment
import one.mixin.android.ui.conversation.web.WebBottomSheetDialogFragment
import one.mixin.android.ui.forward.ForwardActivity
import one.mixin.android.ui.sticker.StickerActivity
import one.mixin.android.ui.url.openUrlWithExtraWeb
import one.mixin.android.ui.url.openWebBottomSheet
import one.mixin.android.ui.wallet.TransactionFragment
import one.mixin.android.ui.wallet.WalletPasswordFragment
import one.mixin.android.util.Attachment
import one.mixin.android.util.AudioPlayer
import one.mixin.android.util.ErrorHandler
import one.mixin.android.util.Session
import one.mixin.android.vo.App
import one.mixin.android.vo.AppCap
import one.mixin.android.vo.ForwardCategory
import one.mixin.android.vo.ForwardMessage
import one.mixin.android.vo.LinkState
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.MessageStatus
import one.mixin.android.vo.Sticker
import one.mixin.android.vo.User
import one.mixin.android.vo.UserRelationship
import one.mixin.android.vo.canNotForward
import one.mixin.android.vo.canNotReply
import one.mixin.android.vo.canRecall
import one.mixin.android.vo.generateConversationId
import one.mixin.android.vo.giphy.Image
import one.mixin.android.vo.supportSticker
import one.mixin.android.vo.toUser
import one.mixin.android.webrtc.CallService
import one.mixin.android.websocket.TransferStickerData
import one.mixin.android.widget.ChatControlView
import one.mixin.android.widget.ContentEditText
import one.mixin.android.widget.DraggableRecyclerView
import one.mixin.android.widget.DraggableRecyclerView.Companion.FLING_DOWN
import one.mixin.android.widget.DraggableRecyclerView.Companion.FLING_UP
import one.mixin.android.widget.MixinHeadersDecoration
import one.mixin.android.widget.gallery.ui.GalleryActivity.Companion.IS_VIDEO
import one.mixin.android.widget.keyboard.KeyboardAwareLinearLayout.OnKeyboardHiddenListener
import one.mixin.android.widget.keyboard.KeyboardAwareLinearLayout.OnKeyboardShownListener
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

class ConversationFragment : LinkFragment(), OnKeyboardShownListener, OnKeyboardHiddenListener,
    OpusAudioRecorder.Callback {

    companion object {
        const val TAG = "ConversationFragment"

        const val CONVERSATION_ID = "conversation_id"
        const val RECIPIENT_ID = "recipient_id"
        const val RECIPIENT = "recipient"
        private const val MESSAGE_ID = "message_id"
        private const val KEY_WORD = "key_word"
        private const val MESSAGES = "messages"

        fun putBundle(
            conversationId: String?,
            recipientId: String?,
            messageId: String?,
            keyword: String?,
            messages: ArrayList<ForwardMessage>?
        ): Bundle =
            Bundle().apply {
                if (conversationId == null && recipientId == null) {
                    throw IllegalArgumentException("lose data")
                }
                messageId?.let {
                    putString(MESSAGE_ID, messageId)
                }
                keyword?.let {
                    putString(KEY_WORD, keyword)
                }
                putString(CONVERSATION_ID, conversationId)
                putString(RECIPIENT_ID, recipientId)
                putParcelableArrayList(MESSAGES, messages)
            }

        fun newInstance(bundle: Bundle) = ConversationFragment().apply { arguments = bundle }
    }

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val chatViewModel: ConversationViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(ConversationViewModel::class.java)
    }

    private var unreadTipCount: Int = 0
    private val chatAdapter: ConversationAdapter by lazy {
        ConversationAdapter(keyword, onItemListener, isGroup, !isPlainMessage()).apply {
            registerAdapterDataObserver(chatAdapterDataObserver)
        }
    }

    private val chatAdapterDataObserver =
        object : RecyclerView.AdapterDataObserver() {
            var oldSize = 0

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                chatAdapter.currentList?.let {
                    oldSize = it.size
                }
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                when {
                    isFirstLoad -> {
                        isFirstLoad = false
                        if (context?.sharedPreferences(RefreshConversationJob.PREFERENCES_CONVERSATION)
                                ?.getBoolean(conversationId, false) == true) {
                            showGroupNotification = true
                            showAlert(0)
                        }
                        val position = if (messageId != null) {
                            unreadCount + 1
                        } else {
                            unreadCount
                        }
                        (chat_rv.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, chat_rv.measuredHeight * 3 / 4)
                        chat_rv.visibility = VISIBLE
                    }
                    isBottom -> {
                        if (chatAdapter.currentList != null && chatAdapter.currentList!!.size > oldSize) {
                            chat_rv.layoutManager?.scrollToPosition(0)
                        }
                    }
                    else -> {
                        if (unreadTipCount > 0) {
                            down_unread.visibility = VISIBLE
                            down_unread.text = "$unreadTipCount"
                        } else {
                            down_unread.visibility = GONE
                        }
                    }
                }
                chatAdapter.currentList?.let {
                    oldSize = it.size
                }
            }
        }

    private fun callVoice() {
        if (LinkState.isOnline(linkState.state)) {
            createConversation {
                CallService.outgoing(requireContext(), recipient!!, conversationId)
            }
        } else {
            toast(R.string.error_no_connection)
        }
    }

    private val onItemListener: ConversationAdapter.OnItemListener by lazy {
        object : ConversationAdapter.OnItemListener() {
            override fun onSelect(isSelect: Boolean, messageItem: MessageItem, position: Int) {
                if (isSelect) {
                    chatAdapter.addSelect(messageItem)
                } else {
                    chatAdapter.removeSelect(messageItem)
                }
                when {
                    chatAdapter.selectSet.isEmpty() -> tool_view.fadeOut()
                    chatAdapter.selectSet.size == 1 -> {
                        try {
                            if (chatAdapter.selectSet.valueAt(0)?.type == MessageCategory.SIGNAL_TEXT.name ||
                                chatAdapter.selectSet.valueAt(0)?.type == MessageCategory.PLAIN_TEXT.name) {
                                tool_view.copy_iv.visibility = VISIBLE
                            } else {
                                tool_view.copy_iv.visibility = GONE
                            }
                        } catch (e: ArrayIndexOutOfBoundsException) {
                            tool_view.copy_iv.visibility = GONE
                        }
                        if (chatAdapter.selectSet.valueAt(0)?.supportSticker() == true) {
                            tool_view.add_sticker_iv.visibility = VISIBLE
                        } else {
                            tool_view.add_sticker_iv.visibility = GONE
                        }
                        if (chatAdapter.selectSet.valueAt(0)?.canNotReply() == true) {
                            tool_view.reply_iv.visibility = GONE
                        } else {
                            tool_view.reply_iv.visibility = VISIBLE
                        }
                    }
                    else -> {
                        tool_view.forward_iv.visibility = VISIBLE
                        tool_view.reply_iv.visibility = GONE
                        tool_view.copy_iv.visibility = GONE
                        tool_view.add_sticker_iv.visibility = GONE
                    }
                }
                if (chatAdapter.selectSet.find { it.canNotForward() } != null) {
                    tool_view.forward_iv.visibility = GONE
                } else {
                    tool_view.forward_iv.visibility = VISIBLE
                }
                chatAdapter.notifyDataSetChanged()
            }

            override fun onLongClick(messageItem: MessageItem, position: Int): Boolean {
                val b = chatAdapter.addSelect(messageItem)
                if (b) {
                    if (messageItem.type == MessageCategory.SIGNAL_TEXT.name ||
                        messageItem.type == MessageCategory.PLAIN_TEXT.name) {
                        tool_view.copy_iv.visibility = VISIBLE
                    } else {
                        tool_view.copy_iv.visibility = GONE
                    }

                    if (messageItem.supportSticker()) {
                        tool_view.add_sticker_iv.visibility = VISIBLE
                    } else {
                        tool_view.add_sticker_iv.visibility = GONE
                    }

                    if (chatAdapter.selectSet.find { it.canNotForward() } != null) {
                        tool_view.forward_iv.visibility = GONE
                    } else {
                        tool_view.forward_iv.visibility = VISIBLE
                    }
                    if (chatAdapter.selectSet.find { it.canNotReply() } != null) {
                        tool_view.reply_iv.visibility = GONE
                    } else {
                        tool_view.reply_iv.visibility = VISIBLE
                    }
                    chatAdapter.notifyDataSetChanged()
                    tool_view.fadeIn()
                }
                return b
            }

            override fun onRetryDownload(messageId: String) {
                chatViewModel.retryDownload(messageId)
            }

            override fun onRetryUpload(messageId: String) {
                chatViewModel.retryUpload(messageId) {
                    toast(R.string.error_retry_upload)
                }
            }

            override fun onCancel(id: String) {
                chatViewModel.cancel(id)
            }

            override fun onImageClick(messageItem: MessageItem, view: View) {
                starTransition = true
                val file = File(messageItem.mediaUrl?.toUri()?.getFilePath())
                if (file.exists()) {
                    DragMediaActivity.show(requireActivity(), view, messageItem)
                } else {
                    toast(R.string.error_file_exists)
                }
            }

            @TargetApi(Build.VERSION_CODES.O)
            override fun onFileClick(messageItem: MessageItem) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O &&
                    messageItem.mediaMimeType.equals("application/vnd.android.package-archive", true)) {
                    if (requireContext().packageManager.canRequestPackageInstalls()) {
                        openMedia(messageItem)
                    } else {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
                    }
                } else {
                    openMedia(messageItem)
                }
            }

            @SuppressLint("CheckResult")
            override fun onUserClick(userId: String) {
                chatViewModel.getUserById(userId).autoDisposable(scopeProvider).subscribe({
                    it?.let {
                        UserBottomSheetDialogFragment.newInstance(it, conversationId).showNow(requireFragmentManager(), UserBottomSheetDialogFragment.TAG)
                    }
                }, {
                    Timber.e(it)
                })
            }

            override fun onUrlClick(url: String) {
                openUrlWithExtraWeb(url, conversationId, requireFragmentManager())
            }

            override fun onMentionClick(name: String) {
            }

            override fun onAddClick() {
                recipient?.let { user ->
                    chatViewModel.updateRelationship(RelationshipRequest(user.userId,
                        RelationshipAction.ADD.name, user.fullName))
                }
            }

            override fun onBlockClick() {
                recipient?.let { user ->
                    chatViewModel.updateRelationship(RelationshipRequest(user.userId,
                        RelationshipAction.BLOCK.name, user.fullName))
                }
            }

            override fun onActionClick(action: String) {
                openUrlWithExtraWeb(action, conversationId, requireFragmentManager())
            }

            override fun onBillClick(messageItem: MessageItem) {
                activity?.addFragment(this@ConversationFragment, TransactionFragment.newInstance(
                    assetId = messageItem.assetId, snapshotId = messageItem.snapshotId), TransactionFragment.TAG)
            }

            override fun onContactCardClick(userId: String) {
                if (userId == Session.getAccountId()) {
                    activity?.addFragment(this@ConversationFragment, ProfileFragment.newInstance(), ProfileFragment.TAG)
                    return
                }
                chatViewModel.getUserById(userId).autoDisposable(scopeProvider).subscribe({
                    it?.let {
                        UserBottomSheetDialogFragment.newInstance(it, conversationId).showNow(requireFragmentManager(), UserBottomSheetDialogFragment.TAG)
                    }
                }, {
                    Timber.e(it)
                })
            }

            override fun onMessageClick(messageId: String?) {
                messageId?.let {
                    chatViewModel.findMessageIndex(conversationId, it).autoDisposable(scopeProvider).subscribe({
                        if (it == 0) {
                            toast(R.string.error_not_found)
                        } else {
                            if (it == chatAdapter.itemCount - 1) {
                                scrollTo(it, 0, action = {
                                    requireContext().mainThreadDelayed({
                                        RxBus.publish(BlinkEvent(messageId))
                                    }, 60)
                                })
                            } else {
                                scrollTo(it + 1, chat_rv.measuredHeight * 3 / 4, action = {
                                    requireContext().mainThreadDelayed({
                                        RxBus.publish(BlinkEvent(messageId))
                                    }, 60)
                                })
                            }
                        }
                    }, {
                    })
                }
            }

            @SuppressLint("CheckResult")
            override fun onCallClick(messageItem: MessageItem) {
                if (!callState.isIdle()) {
                    if (recipient != null && callState.user?.userId == recipient?.userId) {
                        CallActivity.show(requireContext(), recipient)
                    } else {
                        AlertDialog.Builder(requireContext(), R.style.MixinAlertDialogTheme)
                            .setMessage(getString(R.string.chat_call_warning_call))
                            .setNegativeButton(getString(android.R.string.ok)) { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    }
                } else {
                    RxPermissions(requireActivity())
                        .request(Manifest.permission.RECORD_AUDIO)
                        .subscribe({ granted ->
                            if (granted) {
                                callVoice()
                            } else {
                                context?.openPermissionSetting()
                            }
                        }, {
                        })
                }
            }
        }
    }

    private val decoration by lazy {
        MixinHeadersDecoration(chatAdapter)
    }

    private val mentionAdapter: MentionAdapter by lazy {
        MentionAdapter(object : OnUserClickListener {
            override fun onUserClick(keyword: String?, userName: String) {
                chat_control.chat_et.setText(chat_control.chat_et.text.toString().removeEnd(keyword).plus(" @$userName "))
                chat_control.chat_et.setSelection(chat_control.chat_et.text!!.length)
                mentionAdapter.clear()
            }
        })
    }

    private var imageUri: Uri? = null
    private fun createImageUri() = Uri.fromFile(context?.getImagePath()?.createImageTemp())

    private val conversationId: String by lazy {
        var cid = arguments!!.getString(CONVERSATION_ID)
        if (cid.isNullOrBlank()) {
            isFirstMessage = true
            cid = generateConversationId(sender.userId, recipient!!.userId)
        }
        cid
    }

    private var recipient: User? = null

    private val isGroup: Boolean by lazy {
        recipient == null
    }

    private val isBot: Boolean by lazy {
        recipient?.isBot() == true
    }

    private val messageId: String? by lazy {
        arguments!!.getString(MESSAGE_ID, null)
    }

    private val keyword: String? by lazy {
        arguments!!.getString(KEY_WORD, null)
    }

    private val sender: User by lazy { Session.getAccount()!!.toUser() }
    private var app: App? = null

    private var isFirstMessage = false
    private var isFirstLoad = true
    private var isBottom = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recipient = arguments!!.getParcelable<User?>(RECIPIENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_conversation, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val messages = arguments!!.getParcelableArrayList<ForwardMessage>(MESSAGES)
        if (messages != null) {
            sendForwardMessages(messages)
        } else {
            initView()
        }
    }

    private var showGroupNotification = false
    private var disposable: Disposable? = null
    private var paused = false
    private var starTransition = false
    private var recallDisposable: Disposable? = null

    @SuppressLint("AutoDispose")
    override fun onResume() {
        super.onResume()
        input_layout.addOnKeyboardShownListener(this)
        input_layout.addOnKeyboardHiddenListener(this)
        MixinApplication.conversationId = conversationId
        if (isGroup) {
            if (disposable == null || disposable?.isDisposed == true) {
                disposable = RxBus.listen(GroupEvent::class.java)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        if (it.conversationId == conversationId) {
                            showGroupNotification = true
                            showAlert()
                        }
                    }
            }
        }
        if (paused) {
            paused = false
            chat_rv.adapter?.notifyDataSetChanged()
        }
        recallDisposable = RxBus.listen(RecallEvent::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { event ->
                if (chatAdapter.selectSet.any { it.messageId == event.messageId }) {
                    closeTool()
                }
                reply_view.messageItem?.let {
                    if (it.messageId == event.messageId) {
                        reply_view.fadeOut()
                        chat_control.showOtherInput()
                        reply_view.messageItem = null
                    }
                }
            }
    }

    override fun onPause() {
        deleteDialog?.dismiss()
        super.onPause()
        paused = true
        input_layout.removeOnKeyboardShownListener(this)
        input_layout.removeOnKeyboardHiddenListener(this)
        markRead()
        if (disposable?.isDisposed == false) {
            disposable?.dispose()
        }
        AudioPlayer.pause()
        if (OpusAudioRecorder.state != STATE_NOT_INIT) {
            OpusAudioRecorder.get().stop()
        }
        if (chat_control.isRecording) {
            chat_control.cancelExternal()
        }
        MixinApplication.conversationId = null
        recallDisposable?.let { disposable ->
            if (!disposable.isDisposed) {
                disposable.dispose()
            }
        }
    }

    override fun onBackPressed(): Boolean {
        return when {
            tool_view.visibility == VISIBLE -> {
                closeTool()
                true
            }
            chat_control.getVisibleContainer()?.isVisible == true -> {
                chat_control.reset()
                true
            }
            chat_control.isRecording -> {
                OpusAudioRecorder.get().stopRecording(false)
                chat_control.cancelExternal()
                true
            }
            reply_view.visibility == VISIBLE -> {
                reply_view.fadeOut()
                chat_control.showOtherInput()
                true
            }
            else -> false
        }
    }

    private fun hideIfShowBottomSheet() {
        if (sticker_container.isVisible &&
            menu_container.isVisible &&
            gallery_container.isVisible) {
            chat_control.reset()
        }
        if (chat_control.isRecording) {
            OpusAudioRecorder.get().stopRecording(false)
            chat_control.cancelExternal()
        }
        if (reply_view.visibility == VISIBLE) {
            reply_view.fadeOut()
            chat_control.showOtherInput()
        }
    }

    private fun closeTool() {
        chatAdapter.selectSet.clear()
        chatAdapter.notifyDataSetChanged()
        tool_view.fadeOut()
    }

    private fun markRead() {
        chatAdapter.markRead()
    }

    override fun onStop() {
        val draftText = chat_control.chat_et.text
        if (draftText != null) {
            chatViewModel.saveDraft(conversationId, draftText.toString())
        }
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chat_rv?.let { rv ->
            rv.children.forEach {
                val vh = rv.getChildViewHolder(it)
                if (vh != null && vh is BaseViewHolder) {
                    vh.stopListen()
                }
            }
        }
        chatAdapter.unregisterAdapterDataObserver(chatAdapterDataObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        AudioPlayer.release()
    }

    private var firstPosition = 0

    @SuppressLint("CheckResult")
    private fun initView() {
        chat_rv.visibility = INVISIBLE
        if (chat_rv.adapter == null) {
            chat_rv.adapter = chatAdapter
        }
        chat_control.callback = chatControlCallback
        chat_control.activity = requireActivity()
        chat_control.inputLayout = input_layout
        chat_control.stickerContainer = sticker_container
        chat_control.menuContainer = menu_container
        chat_control.galleryContainer = gallery_container
        chat_control.recordTipView = record_tip_tv
        chat_control.setCircle(record_circle)
        chat_control.chat_et.setCommitContentListener(object : ContentEditText.OnCommitContentListener {
            override fun onCommitContent(inputContentInfo: InputContentInfoCompat?, flags: Int, opts: Bundle?): Boolean {
                if (inputContentInfo != null) {
                    val url = inputContentInfo.contentUri.getFilePath(requireContext())
                        ?: return false
                    sendImageMessage(url.toUri())
                }
                return true
            }
        })
        chat_rv.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, true)
        chat_rv.addItemDecoration(decoration)
        chat_rv.itemAnimator = null

        chat_rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                firstPosition = (chat_rv.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                if (firstPosition > 0) {
                    if (isBottom) {
                        isBottom = false
                        showAlert()
                    }
                } else {
                    if (!isBottom) {
                        isBottom = true
                        hideAlert()
                    }
                    unreadTipCount = 0
                    down_unread.visibility = GONE
                }
            }
        })
        chat_rv.callback = object : DraggableRecyclerView.Callback {
            override fun onScroll(dis: Float) {
                val currentContainer = chat_control.getDraggableContainer()
                if (currentContainer != null) {
                    dragChatControl(dis)
                }
            }

            override fun onRelease(fling: Int) {
                releaseChatControl(fling)
            }
        }
        action_bar.left_ib.setOnClickListener {
            activity?.onBackPressed()
        }

        if (isGroup) {
            renderGroup()
        } else {
            renderUser(recipient!!)
        }

        bg_quick_flag.setOnClickListener {
            if (chat_rv.scrollState == RecyclerView.SCROLL_STATE_SETTLING) {
                chat_rv.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0f, 0f, 0))
            }
            scrollTo(0)
            unreadTipCount = 0
            down_unread.visibility = GONE
        }
        chatViewModel.searchConversationById(conversationId)
            .autoDisposable(scopeProvider).subscribe({
                it?.draft?.let { str ->
                    if (isAdded) {
                        chat_control.chat_et.setText(str)
                    }
                }
            }, {
                Timber.e(it)
            })
        tool_view.close_iv.setOnClickListener { activity?.onBackPressed() }
        tool_view.delete_iv.setOnClickListener {
            chatAdapter.selectSet.filter { it.type.endsWith("_AUDIO") }.forEach {
                if (AudioPlayer.get().isPlay(it.messageId)) {
                    AudioPlayer.get().pause()
                }
            }
            deleteMessage(chatAdapter.selectSet.toList())
            closeTool()
        }
        reply_view.reply_close_iv.setOnClickListener {
            reply_view.fadeOut()
            chat_control.showOtherInput()
        }
        tool_view.copy_iv.setOnClickListener {
            try {
                context?.getClipboardManager()?.primaryClip =
                    ClipData.newPlainText(null, chatAdapter.selectSet.valueAt(0)?.content)
                context?.toast(R.string.copy_success)
            } catch (e: ArrayIndexOutOfBoundsException) {
            }
            closeTool()
        }
        tool_view.forward_iv.setOnClickListener {
            val list = ArrayList<ForwardMessage>()
            list += chatAdapter.selectSet.sortedBy { it.createdAt }.map {
                when {
                    it.type.endsWith("_TEXT") -> ForwardMessage(ForwardCategory.TEXT.name, content = it.content)
                    it.type.endsWith("_IMAGE") -> ForwardMessage(ForwardCategory.IMAGE.name, id = it.messageId)
                    it.type.endsWith("_DATA") -> ForwardMessage(ForwardCategory.DATA.name, id = it.messageId)
                    it.type.endsWith("_VIDEO") -> ForwardMessage(ForwardCategory.VIDEO.name, id = it.messageId)
                    it.type.endsWith("_CONTACT") -> ForwardMessage(ForwardCategory.CONTACT.name, sharedUserId = it.sharedUserId)
                    it.type.endsWith("_STICKER") -> ForwardMessage(ForwardCategory.STICKER.name, id = it.messageId)
                    it.type.endsWith("_AUDIO") -> ForwardMessage(ForwardCategory.AUDIO.name, id = it.messageId)
                    else -> ForwardMessage(ForwardCategory.TEXT.name)
                }
            }
            ForwardActivity.show(requireContext(), list)
            closeTool()
        }
        tool_view.add_sticker_iv.setOnClickListener {
            if (chatAdapter.selectSet.isEmpty()) {
                return@setOnClickListener
            }
            val messageItem = chatAdapter.selectSet.valueAt(0)
            messageItem?.let { m ->
                val isSticker = messageItem.type.endsWith("STICKER")
                if (isSticker && m.stickerId != null) {
                    val request = StickerAddRequest(stickerId = m.stickerId)
                    chatViewModel.addSticker(request)
                        .subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
                        .map { r ->
                            if (r.isSuccess) {
                                val personalAlbum = chatViewModel.getPersonalAlbums()
                                if (personalAlbum == null) { // not add any personal sticker yet
                                    chatViewModel.refreshStickerAlbums()
                                } else {
                                    chatViewModel.addStickerLocal(r.data as Sticker, personalAlbum.albumId)
                                }
                            }
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .autoDisposable(scopeProvider)
                        .subscribe({
                            closeTool()
                            requireContext().toast(R.string.add_success)
                        }, {
                            ErrorHandler.handleError(it)
                            requireContext().toast(R.string.sticker_add_failed)
                        })
                } else {
                    val url = m.mediaUrl
                    url?.let {
                        val uri = url.toUri()
                        val mimeType = getMimeType(uri)
                        if (mimeType?.isImageSupport() == true) {
                            StickerActivity.show(requireContext(), url = it, showAdd = true)
                        } else {
                            requireContext().toast(R.string.sticker_add_invalid_format)
                        }
                    }
                }
            }
        }

        tool_view.reply_iv.setOnClickListener {
            if (chatAdapter.selectSet.isEmpty()) {
                return@setOnClickListener
            }
            chatAdapter.selectSet.valueAt(0)?.let {
                reply_view.bind(it)
            }
            if (!reply_view.isVisible) {
                reply_view.fadeIn()
                chat_control.hideOtherInput()
                chat_control.reset()
                if (chat_control.isRecording) {
                    OpusAudioRecorder.get().stopRecording(false)
                    chat_control.cancelExternal()
                }
                chat_control.chat_et.showKeyboard()
            }
            closeTool()
        }

        callState.observe(this, Observer { info ->
            chat_control.calling = info.callState != CallService.CallState.STATE_IDLE
        })
        bindData()
    }

    private var deleteDialog: AlertDialog? = null
    private fun deleteMessage(messages: List<MessageItem>) {
        deleteDialog?.dismiss()
        val showRecall = messages.all { item ->
            item.userId == sender.userId && item.status != MessageStatus.SENDING.name && !item.createdAt.lateOneHours() && item.canRecall()
        }
        val deleteDialogLayout = generateDeleteDialogLayout()
        deleteDialog = AlertDialog.Builder(requireContext(), R.style.MixinAlertDialogTheme)
            .setMessage(getString(R.string.chat_delete_message, messages.size))
            .setView(deleteDialogLayout)
            .create()
        if (showRecall) {
            deleteDialogLayout.delete_everyone.setOnClickListener {
                if (defaultSharedPreferences.getBoolean(Constants.Account.PREF_RECALL_SHOW, true)) {
                    deleteDialog?.dismiss()
                    deleteAlert(messages)
                    defaultSharedPreferences.putBoolean(Constants.Account.PREF_RECALL_SHOW, false)
                } else {
                    chatViewModel.sendRecallMessage(conversationId, sender, messages)
                    deleteDialog?.dismiss()
                }
            }
            deleteDialogLayout.delete_everyone.visibility = VISIBLE
        } else {
            deleteDialogLayout.delete_everyone.visibility = GONE
        }
        deleteDialogLayout.delete_me.setOnClickListener {
            chatViewModel.deleteMessages(messages)
            deleteDialog?.dismiss()
        }
        deleteDialog?.show()
    }

    private fun generateDeleteDialogLayout(): View {
        return LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_delete, null, false).apply {
            this.delete_cancel.setOnClickListener {
                deleteDialog?.dismiss()
            }
        }
    }

    private var deleteAlertDialog: AlertDialog? = null
    private fun deleteAlert(messages: List<MessageItem>) {
        deleteAlertDialog?.dismiss()
        deleteDialog = AlertDialog.Builder(requireContext(), R.style.MixinAlertDialogTheme)
            .setMessage(getString(R.string.chat_recall_delete_alert))
            .setNegativeButton(getString(android.R.string.ok)) { dialog, _ ->
                chatViewModel.sendRecallMessage(conversationId, sender, messages)
                dialog.dismiss()
            }
            .setNeutralButton(R.string.chat_recall_delete_more) { dialog, _ ->
                context?.openUrl(getString(R.string.chat_delete_url))
                dialog.dismiss()
            }
            .create()
        deleteDialog?.show()
    }

    private fun liveDataMessage(unreadCount: Int) {
        chatViewModel.getMessages(conversationId, unreadCount).observe(this@ConversationFragment, Observer {
            it?.let {
                if (it.size > 0) {
                    isFirstMessage = false
                }
                if (!isFirstLoad && !isBottom && it.size > chatAdapter.getRealItemCount()) {
                    unreadTipCount += (it.size - chatAdapter.getRealItemCount())
                }
                chatAdapter.hasBottomView = !isGroup &&
                    recipient?.relationship == UserRelationship.STRANGER.name &&
                    it.find { it != null && it.userId == sender.userId } == null
                if (isFirstLoad && messageId == null && unreadCount > 0) {
                    chatAdapter.unreadIndex = unreadCount
                } else if (it.size != chatAdapter.getRealItemCount()) {
                    chatAdapter.unreadIndex = null
                }
                if (it.size > 0) {
                    chatViewModel.markMessageRead(conversationId, sender.userId)
                }
            }
            chatAdapter.submitList(it)
        })
    }

    private var unreadCount = 0
    private fun bindData() {
        lifecycleScope.launch(Dispatchers.IO) {
            unreadCount = if (!messageId.isNullOrEmpty()) {
                chatViewModel.findMessageIndexSync(conversationId, messageId!!)
            } else {
                chatViewModel.indexUnread(conversationId)
            }
            withContext(Dispatchers.Main) {
                if (!isAdded) {
                    return@withContext
                }
                liveDataMessage(unreadCount)
            }
        }

        if (isBot) {
            lifecycleScope.launch(Dispatchers.IO) {
                val botsString = defaultSharedPreferences.getString(PREF_RECENT_USED_BOTS, null)
                if (botsString != null) {
                    var botsList = botsString.split("=")
                    if (botsList.size == 1 && !botsList[0].isUUID()) {
                        getPreviousVersionBotsList()?.let {
                            botsList = it
                        }
                    }
                    if (botsList.isNullOrEmpty()) {
                        defaultSharedPreferences.putString(PREF_RECENT_USED_BOTS, recipient!!.userId)
                        return@launch
                    }

                    val arr = botsList.filter { it != recipient!!.userId }
                        .toMutableList()
                        .also {
                            if (it.size >= RECENT_USED_BOTS_MAX_COUNT) {
                                it.dropLast(1)
                            }
                            it.add(0, recipient!!.userId)
                        }
                    defaultSharedPreferences.putString(PREF_RECENT_USED_BOTS, arr.joinToString("="))
                } else {
                    defaultSharedPreferences.putString(PREF_RECENT_USED_BOTS, recipient!!.userId)
                }
            }
            chat_control.showBot()
        } else {
            chat_control.hideBot()
            chatViewModel.getApp(conversationId, recipient?.userId).observe(this, Observer { list ->
                val type = if (isGroup) {
                    AppCap.GROUP.name
                } else {
                    AppCap.CONTACT.name
                }
                appList = list.filter {
                    it.capabilites?.contains(type) == true
                }
                appList?.let {
                    (requireFragmentManager().findFragmentByTag(MenuFragment.TAG) as? MenuFragment)?.setAppList(it)
                }
            })
        }
    }

    private fun getPreviousVersionBotsList(): List<String>? {
        defaultSharedPreferences.getString(PREF_RECENT_USED_BOTS, null)?.let { botsString ->
            return botsString.deserialize<Array<String>>()?.toList()
        } ?: return null
    }

    private var appList: List<App>? = null

    private fun sendForwardMessages(messages: List<ForwardMessage>) {
        createConversation {
            initView()
            messages.let {
                for (item in it) {
                    if (item.id != null) {
                        sendForwardMessage(item.id)
                    } else {
                        when (item.type) {
                            ForwardCategory.CONTACT.name -> {
                                sendContactMessage(item.sharedUserId!!)
                            }
                            ForwardCategory.IMAGE.name -> {
                                sendImageMessage(Uri.parse(item.mediaUrl), item.mimeType)
                            }
                            ForwardCategory.DATA.name -> {
                                context?.getAttachment(Uri.parse(item.mediaUrl))?.let {
                                    sendAttachmentMessage(it)
                                }
                            }
                            ForwardCategory.VIDEO.name -> {
                                sendVideoMessage(Uri.parse(item.mediaUrl))
                            }
                            ForwardCategory.TEXT.name -> {
                                item.content?.let { sendMessage(it) }
                            }
                        }
                    }
                }
                scrollToDown()
            }
        }
    }

    private inline fun createConversation(crossinline action: () -> Unit) {
        if (isFirstMessage) {
            doAsync {
                chatViewModel.initConversation(conversationId, recipient!!, sender)
                isFirstMessage = false

                uiThread {
                    if (isAdded) {
                        action()
                    }
                }
            }
        } else {
            action()
        }
    }

    private fun isPlainMessage(): Boolean {
        return if (isGroup) {
            false
        } else {
            this.isBot
        }
    }

    @SuppressLint("CheckResult")
    private fun sendImageMessage(uri: Uri, mimeType: String? = null) {
        createConversation {
            chatViewModel.sendImageMessage(conversationId, sender, uri, isPlainMessage(), mimeType)
                ?.autoDisposable(scopeProvider)?.subscribe({
                    when (it) {
                        0 -> {
                            scrollToDown()
                            markRead()
                        }
                        -1 -> context?.toast(R.string.error_image)
                        -2 -> context?.toast(R.string.error_format)
                    }
                }, {
                    context?.toast(R.string.error_image)
                })
        }
    }

    private fun sendGiphy(image: Image, previewUrl: String) {
        createConversation {
            chatViewModel.sendGiphyMessage(conversationId, sender.userId, image, isPlainMessage(), previewUrl)
            chat_rv.postDelayed({
                scrollToDown()
            }, 1000)
        }
    }

    override fun onCancel() {
        chat_control?.cancelExternal()
    }

    override fun sendAudio(file: File, duration: Long, waveForm: ByteArray) {
        if (duration < 500) {
            file.deleteOnExit()
        } else {
            createConversation {
                chatViewModel.sendAudioMessage(conversationId, sender, file, duration, waveForm, isPlainMessage())
                scrollToDown()
            }
        }
    }

    private fun sendVideoMessage(uri: Uri) {
        createConversation {
            chatViewModel.sendVideoMessage(conversationId, sender.userId, uri, isPlainMessage())
            chat_rv.postDelayed({
                scrollToDown()
            }, 1000)
        }
    }

    private fun sendForwardMessage(id: String?) {
        id?.let {
            createConversation {
                chatViewModel.sendFordMessage(conversationId, sender, it, isPlainMessage())
                    .autoDisposable(scopeProvider).subscribe({
                        if (it == 0) {
                            toast(R.string.error_file_exists)
                        }
                    }, {
                        Timber.e(id)
                    })
            }
        }
    }

    private fun sendAttachmentMessage(attachment: Attachment) {
        createConversation {
            chatViewModel.sendAttachmentMessage(conversationId, sender, attachment, isPlainMessage())
            scrollToDown()
            markRead()
        }
    }

    private fun sendStickerMessage(stickerId: String) {
        createConversation {
            chatViewModel.sendStickerMessage(conversationId, sender,
                TransferStickerData(stickerId), isPlainMessage())
            scrollToDown()
            markRead()
        }
    }

    private fun sendContactMessage(userId: String) {
        createConversation {
            chatViewModel.sendContactMessage(conversationId, sender, userId, isPlainMessage())
            scrollToDown()
            markRead()
        }
    }

    private fun sendMessage(message: String) {
        if (message.isNotBlank()) {
            chat_control.chat_et.setText("")
            createConversation {
                chatViewModel.sendTextMessage(conversationId, sender, message, isPlainMessage())
                scrollToDown()
                markRead()
            }
        }
    }

    private fun sendReplyMessage(message: String) {
        if (message.isNotBlank() && reply_view.messageItem != null) {
            chat_control.chat_et.setText("")
            createConversation {
                chatViewModel.sendReplyMessage(conversationId, sender, message, reply_view.messageItem!!, isPlainMessage())
                reply_view.fadeOut()
                chat_control.showOtherInput()
                reply_view.messageItem = null
                scrollToDown()
                markRead()
            }
        }
    }

    private var groupName: String? = null
    private var groupNumber: Int = 0

    @SuppressLint("SetTextI18n")
    private fun renderGroup() {
        action_bar.avatar_iv.visibility = VISIBLE
        action_bar.avatar_iv.setOnClickListener {
            showGroupNotification = false
            hideAlert()
            showGroupBottomSheet(false)
        }
        group_flag.setOnClickListener {
            showGroupNotification = false
            requireContext().sharedPreferences(RefreshConversationJob.PREFERENCES_CONVERSATION)
                .putBoolean(conversationId, false)
            hideAlert()
            showGroupBottomSheet(true)
        }
        chatViewModel.getConversationById(conversationId).observe(this, Observer {
            it?.let {
                groupName = it.name
                action_bar.setSubTitle(groupName
                    ?: "", getString(R.string.title_participants, groupNumber))
                action_bar.avatar_iv.setGroup(it.iconUrl)
            }
        })
        chatViewModel.getGroupParticipantsLiveData(conversationId).observe(this, Observer { users ->
            users?.let {
                groupNumber = it.size
                action_bar.setSubTitle(groupName
                    ?: "", getString(R.string.title_participants, groupNumber))

                val userIds = arrayListOf<String>()
                users.mapTo(userIds) { it.userId }
                if (userIds.contains(Session.getAccountId())) {
                    chat_control.visibility = VISIBLE
                    bottom_cant_send.visibility = GONE
                } else {
                    chat_control.visibility = INVISIBLE
                    bottom_cant_send.visibility = VISIBLE
                    chat_control.chat_et.hideKeyboard()
                }
            }
            mentionAdapter.list = users
            mentionAdapter.notifyDataSetChanged()
        })
    }

    private fun showGroupBottomSheet(expand: Boolean) {
        hideIfShowBottomSheet()
        val bottomSheetDialogFragment = GroupBottomSheetDialogFragment.newInstance(conversationId = conversationId, expand = expand)
        bottomSheetDialogFragment.showNow(requireFragmentManager(), GroupBottomSheetDialogFragment.TAG)
        bottomSheetDialogFragment.callback = object : GroupBottomSheetDialogFragment.Callback {
            override fun onDelete() {
                activity?.finish()
            }
        }
    }

    override fun onKeyboardHidden() {
        chat_control.toggleKeyboard(false)
    }

    override fun onKeyboardShown() {
        chat_control.toggleKeyboard(true)
    }

    private fun renderUser(user: User) {
        chatAdapter.recipient = user
        renderUserInfo(user)
        chatViewModel.findUserById(user.userId).observe(this, Observer {
            it?.let {
                recipient = it
                renderUserInfo(it)
            }
        })
        action_bar.avatar_iv.setOnClickListener {
            hideIfShowBottomSheet()
            UserBottomSheetDialogFragment.newInstance(user, conversationId).showNow(requireFragmentManager(), UserBottomSheetDialogFragment.TAG)
        }
        bottom_unblock.setOnClickListener {
            recipient?.let { user ->
                chatViewModel.updateRelationship(RelationshipRequest(user.userId,
                    RelationshipAction.UNBLOCK.name, user.fullName))
            }
        }

        if (user.isBot()) {
            doAsync {
                app = chatViewModel.findAppById(user.appId!!)
                if (app != null && app!!.creatorId == Session.getAccountId()) {
                    uiThread {
                        initMenuLayout(true)
                    }
                }
            }
        }
    }

    private fun renderUserInfo(user: User) {
        action_bar.setSubTitle(user.fullName ?: "", user.identityNumber)
        action_bar.avatar_iv.visibility = VISIBLE
        action_bar.avatar_iv.setTextSize(16f)
        action_bar.avatar_iv.setInfo(user.fullName, user.avatarUrl, user.userId)
        user.let {
            if (it.relationship == UserRelationship.BLOCKING.name) {
                chat_control.visibility = INVISIBLE
                bottom_unblock.visibility = VISIBLE
                chat_control.chat_et.hideKeyboard()
            } else {
                chat_control.visibility = VISIBLE
                bottom_unblock.visibility = GONE
            }
        }
    }

    private fun clickSticker() {
        val stickerAlbumFragment = requireFragmentManager().findFragmentByTag(StickerAlbumFragment.TAG)
        if (stickerAlbumFragment == null) {
            initStickerLayout()
        }
    }

    private fun clickMenu() {
        val menuFragment = requireFragmentManager().findFragmentByTag(MenuFragment.TAG)
        if (menuFragment == null) {
            initMenuLayout()
        }
    }

    private fun clickGallery() {
        val galleryAlbumFragment = requireFragmentManager().findFragmentByTag(GalleryAlbumFragment.TAG)
        if (galleryAlbumFragment == null) {
            initGalleryLayout()
        }
    }

    private fun initGalleryLayout() {
        val galleryAlbumFragment = GalleryAlbumFragment.newInstance()
        galleryAlbumFragment.callback = object : GalleryCallback {
            override fun onItemClick(pos: Int, uri: Uri, isVideo: Boolean) {
                if (isVideo) {
                    sendVideoMessage(uri)
                } else {
                    sendImageMessage(uri)
                }
                releaseChatControl(FLING_DOWN)
            }

            override fun onCameraClick() {
                openCamera()
            }
        }
        galleryAlbumFragment.rvCallback = object : DraggableRecyclerView.Callback {
            override fun onScroll(dis: Float) {
                val currentContainer = chat_control.getDraggableContainer()
                if (currentContainer != null) {
                    dragChatControl(dis)
                }
            }

            override fun onRelease(fling: Int) {
                releaseChatControl(fling)
            }
        }
        activity?.replaceFragment(galleryAlbumFragment, R.id.gallery_container, GalleryAlbumFragment.TAG)
    }

    private fun initMenuLayout(isSelfCreatedBot: Boolean = false) {
        val menuFragment = MenuFragment.newInstance(isGroup, isBot, isSelfCreatedBot)
        activity?.replaceFragment(menuFragment, R.id.menu_container, MenuFragment.TAG)
        appList?.let {
            menuFragment.setAppList(it)
        }
        menuFragment.callback = object : MenuFragment.Callback {
            override fun onMenuClick(menu: Menu) {
                chat_control.reset()
                when (menu.type) {
                    MenuType.Camera -> {
                        openCamera()
                    }
                    MenuType.File -> {
                        RxPermissions(requireActivity())
                            .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                            .subscribe({ granted ->
                                if (granted) {
                                    selectDocument()
                                } else {
                                    context?.openPermissionSetting()
                                }
                            }, {
                            })
                    }
                    MenuType.Transfer -> {
                        if (Session.getAccount()?.hasPin == true) {
                            recipient?.let {
                                TransferFragment.newInstance(it.userId, supportSwitchAsset = true)
                                    .showNow(requireFragmentManager(), TransferFragment.TAG)
                            }
                        } else {
                            requireFragmentManager().inTransaction {
                                setCustomAnimations(R.anim.slide_in_bottom, R.anim.slide_out_bottom, R
                                    .anim.slide_in_bottom, R.anim.slide_out_bottom)
                                    .add(R.id.container, WalletPasswordFragment.newInstance(), WalletPasswordFragment.TAG)
                                    .addToBackStack(null)
                            }
                        }
                    }
                    MenuType.Contact -> {
                        requireFragmentManager().inTransaction {
                            setCustomAnimations(R.anim.slide_in_bottom, R.anim.slide_out_bottom, R
                                .anim.slide_in_bottom, R.anim.slide_out_bottom)
                                .add(R.id.container,
                                    FriendsFragment.newInstance(conversationId).apply {
                                        setOnFriendClick {
                                            sendContactMessage(it.userId)
                                        }
                                    }, FriendsFragment.TAG)
                                .addToBackStack(null)
                        }
                    }
                    MenuType.Voice -> {
                        if (!callState.isIdle()) {
                            if (recipient != null && callState.user?.userId == recipient?.userId) {
                                CallActivity.show(requireContext(), recipient)
                            } else {
                                AlertDialog.Builder(requireContext(), R.style.MixinAlertDialogTheme)
                                    .setMessage(getString(R.string.chat_call_warning_call))
                                    .setNegativeButton(getString(android.R.string.ok)) { dialog, _ ->
                                        dialog.dismiss()
                                    }
                                    .show()
                            }
                        } else {
                            RxPermissions(requireActivity())
                                .request(Manifest.permission.RECORD_AUDIO)
                                .subscribe({ granted ->
                                    if (granted) {
                                        callVoice()
                                    } else {
                                        context?.openPermissionSetting()
                                    }
                                }, {
                                })
                        }
                    }
                    MenuType.App -> {
                        menu.homeUri?.let {
                            chat_control.chat_et.hideKeyboard()
                            openWebBottomSheet(it, conversationId, requireFragmentManager())
                        }
                    }
                }
            }
        }
    }

    private fun initStickerLayout() {
        val stickerAlbumFragment = StickerAlbumFragment.newInstance()
        activity?.replaceFragment(stickerAlbumFragment, R.id.sticker_container, StickerAlbumFragment.TAG)
        stickerAlbumFragment.setCallback(object : StickerAlbumFragment.Callback {
            override fun onStickerClick(stickerId: String) {
                if (isAdded) {
                    if (sticker_container.height != input_layout.keyboardHeight) {
                        sticker_container.animateHeight(sticker_container.height, input_layout.keyboardHeight)
                    }
                    sendStickerMessage(stickerId)
                }
            }

            override fun onGiphyClick(image: Image, previewUrl: String) {
                if (isAdded) {
                    sendGiphy(image, previewUrl)
                }
            }
        })
        stickerAlbumFragment.rvCallback = object : DraggableRecyclerView.Callback {
            override fun onScroll(dis: Float) {
                val currentContainer = chat_control.getDraggableContainer()
                if (currentContainer != null) {
                    dragChatControl(dis)
                }
            }

            override fun onRelease(fling: Int) {
                releaseChatControl(fling)
            }
        }
    }

    private fun scrollToDown() {
        chat_rv.layoutManager?.scrollToPosition(0)
        if (firstPosition > PAGE_SIZE * 6) {
            chatAdapter.notifyDataSetChanged()
        }
    }

    private fun scrollTo(position: Int, offset: Int = -1, delay: Long = 30, action: (() -> Unit)? = null) {
        chat_rv.postDelayed({
            if (isAdded) {
                if (position == 0 && offset == 0) {
                    chat_rv.layoutManager?.scrollToPosition(0)
                } else if (offset == -1) {
                    (chat_rv.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
                } else {
                    (chat_rv.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, offset)
                }
                action?.let { it() }
                if (abs(firstPosition - position) > PAGE_SIZE * 6) {
                    chatAdapter.notifyDataSetChanged()
                }
            }
        }, delay)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_GALLERY && resultCode == Activity.RESULT_OK) {
            data?.data?.let {
                if (data.hasExtra(IS_VIDEO)) {
                    sendVideoMessage(it)
                } else {
                    sendImageMessage(it)
                }
            }
        } else if (requestCode == REQUEST_CAMERA && resultCode == Activity.RESULT_OK) {
            imageUri?.let { imageUri ->
                showPreview(imageUri) { sendImageMessage(it) }
            }
        } else if (requestCode == REQUEST_FILE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            context?.getAttachment(uri)?.let {
                AlertDialog.Builder(requireContext(), R.style.MixinAlertDialogTheme)
                    .setMessage(if (isGroup) {
                        requireContext().getString(R.string.send_file_group, it.filename, groupName)
                    } else {
                        requireContext().getString(R.string.send_file, it.filename, recipient?.fullName)
                    })
                    .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .setPositiveButton(R.string.send) { dialog, _ ->
                        sendAttachmentMessage(it)
                        dialog.dismiss()
                    }.show()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun openMedia(messageItem: MessageItem) {
        val intent = Intent()
        intent.action = Intent.ACTION_VIEW
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            messageItem.mediaUrl?.let {
                val file = File(it)
                val uri = if (!file.exists()) {
                    Uri.parse(it)
                } else {
                    requireContext().getUriForFile(file)
                }
                intent.setDataAndType(uri, messageItem.mediaMimeType)
                requireContext().startActivity(intent)
            }
        } catch (e: ActivityNotFoundException) {
            context?.toast(R.string.error_unable_to_open_media)
        }
    }

    @SuppressLint("CheckResult", "AutoDispose")
    private fun openCamera() {
        RxPermissions(requireActivity())
            .request(Manifest.permission.CAMERA)
            .subscribe({ granted ->
                if (granted) {
                    imageUri = createImageUri()
                    imageUri?.let {
                        openCamera(it)
                    }
                } else {
                    context?.openPermissionSetting()
                }
            }, {
            })
    }

    private fun showAlert(duration: Long = 100) {
        if (isGroup) {
            if (showGroupNotification) {
                group_flag.visibility = VISIBLE
            } else {
                group_flag.visibility = GONE
            }
            if (!isBottom) {
                down_flag.visibility = VISIBLE
            } else {
                down_flag.visibility = GONE
            }
            if (bg_quick_flag.translationY != 0f) {
                bg_quick_flag.translationY(0f, duration)
            }
        } else {
            if (bg_quick_flag.translationY != 0f) {
                bg_quick_flag.translationY(0f, duration)
            }
        }
    }

    private fun hideAlert() {
        if (isGroup) {
            if (showGroupNotification) {
                group_flag.visibility = VISIBLE
            } else {
                group_flag.visibility = GONE
            }
            if (isBottom) {
                if (showGroupNotification) {
                    bg_quick_flag.translationY(requireContext().dpToPx(60f).toFloat(), 100)
                } else if (isBottom) {
                    bg_quick_flag.translationY(requireContext().dpToPx(130f).toFloat(), 100)
                }
            }
        } else {
            bg_quick_flag.translationY(requireContext().dpToPx(130f).toFloat(), 100)
        }
    }

    private var previewDialogFragment: PreviewDialogFragment? = null

    private fun showPreview(uri: Uri, action: (Uri) -> Unit) {
        if (previewDialogFragment == null) {
            previewDialogFragment = PreviewDialogFragment.newInstance()
        }
        previewDialogFragment?.show(requireFragmentManager(), uri, action)
    }

    private val voiceAlert by lazy {
        AlertDialog.Builder(requireContext(), R.style.MixinAlertDialogTheme)
            .setMessage(getString(R.string.chat_call_warning_voice))
            .setNegativeButton(getString(android.R.string.ok)) { dialog, _ ->
                dialog.dismiss()
            }.create()
    }

    private fun showVoiceWarning() {
        if (!voiceAlert.isShowing) {
            voiceAlert.show()
        }
    }

    private fun dragChatControl(dis: Float) {
        val currentContainer = chat_control.getDraggableContainer() ?: return
        val params = currentContainer.layoutParams
        val targetH = params.height - dis.toInt()
        val total = (requireContext().screenHeight() * 2) / 3
        if (targetH <= 0 || targetH >= total) return

        params.height = targetH
        currentContainer.layoutParams = params
    }

    private fun releaseChatControl(fling: Int) {
        val currentContainer = chat_control.getDraggableContainer() ?: return
        val curH = currentContainer.height
        val max = (requireContext().screenHeight() * 2) / 3
        val maxMid = input_layout.keyboardHeight + (max - input_layout.keyboardHeight) / 2
        val minMid = input_layout.keyboardHeight / 2
        val targetH = if (curH > input_layout.keyboardHeight) {
            if (fling == FLING_UP) {
                max
            } else if (fling == FLING_DOWN) {
                input_layout.keyboardHeight
            } else {
                if (curH <= maxMid) {
                    input_layout.keyboardHeight
                } else {
                    max
                }
            }
        } else if (curH < input_layout.keyboardHeight) {
            if (fling == FLING_UP) {
                input_layout.keyboardHeight
            } else if (fling == FLING_DOWN) {
                0
            } else {
                if (curH > minMid) {
                    input_layout.keyboardHeight
                } else {
                    0
                }
            }
        } else {
            if (fling == FLING_UP) {
                max
            } else if (fling == FLING_DOWN) {
                0
            } else {
                input_layout.keyboardHeight
            }
        }
        if (targetH == 0) {
            chat_control.reset()
        }
        currentContainer.animateHeight(curH, targetH)
        RxBus.publish(DragReleaseEvent(targetH == max))
    }

    private val chatControlCallback = object : ChatControlView.Callback {
        override fun onStickerClick() {
            clickSticker()
        }

        override fun onSendClick(text: String) {
            if (reply_view.isVisible && reply_view.messageItem != null) {
                sendReplyMessage(text)
            } else {
                sendMessage(text)
            }
        }

        override fun onRecordStart(audio: Boolean) {
            AudioPlayer.get().pause()
            OpusAudioRecorder.get().startRecording(this@ConversationFragment)
        }

        override fun isReady(): Boolean {
            return OpusAudioRecorder.state == STATE_RECORDING
        }

        override fun onRecordEnd() {
            OpusAudioRecorder.get().stopRecording(true)
        }

        override fun onRecordCancel() {
            OpusAudioRecorder.get().stopRecording(false)
        }

        override fun onCalling() {
            showVoiceWarning()
        }

        override fun onMenuClick() {
            clickMenu()
        }

        override fun onBotClick() {
            hideIfShowBottomSheet()
            app?.let {
                chat_control.chat_et.hideKeyboard()
                val dialog = WebBottomSheetDialogFragment.newInstance(it.homeUri, conversationId, appName = it.name, appAvatar = recipient?.avatarUrl)
                dialog.showNow(requireFragmentManager(), WebBottomSheetDialogFragment.TAG)
            }
        }

        override fun onGalleryClick() {
            clickGallery()
        }

        override fun onDragChatControl(dis: Float) {
            dragChatControl(dis)
        }

        override fun onReleaseChatControl(fling: Int) {
            releaseChatControl(fling)
        }
    }
}