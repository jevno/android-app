package one.mixin.android.ui.conversation

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_draggable_recycler_view.*
import one.mixin.android.R
import one.mixin.android.RxBus
import one.mixin.android.event.DragReleaseEvent
import one.mixin.android.extension.realSize
import one.mixin.android.extension.withArgs
import one.mixin.android.ui.conversation.adapter.GalleryCallback
import one.mixin.android.ui.conversation.adapter.GalleryItemAdapter
import one.mixin.android.ui.conversation.adapter.StickerSpacingItemDecoration
import one.mixin.android.widget.DraggableRecyclerView
import one.mixin.android.widget.DraggableRecyclerView.Companion.DIRECTION_NONE
import one.mixin.android.widget.DraggableRecyclerView.Companion.DIRECTION_TOP_2_BOTTOM
import one.mixin.android.widget.gallery.internal.entity.Album
import one.mixin.android.widget.gallery.internal.entity.Item
import one.mixin.android.widget.gallery.internal.model.AlbumMediaCollection
import org.jetbrains.anko.dip

class GalleryItemFragment : Fragment(), AlbumMediaCollection.AlbumMediaCallbacks {
    companion object {
        const val TAG = "GalleryItemFragment"
        const val ARGS_ALBUM = "args_album"
        const val ARGS_NEED_CAMERA = "args_need_camera"
        const val PADDING = 10
        const val COLUMN = 3

        fun newInstance(album: Album, needCamera: Boolean) = GalleryItemFragment().withArgs {
            putParcelable(ARGS_ALBUM, album)
            putBoolean(ARGS_NEED_CAMERA, needCamera)
        }
    }

    var callback: GalleryCallback? = null
    var rvCallback: DraggableRecyclerView.Callback? = null

    private var disposable: Disposable? = null

    private val album: Album by lazy { arguments!!.getParcelable<Album>(ARGS_ALBUM) }
    private val needCamera: Boolean by lazy { arguments!!.getBoolean(ARGS_NEED_CAMERA) }

    private val padding: Int by lazy {
        requireContext().dip(PADDING)
    }

    private val adapter: GalleryItemAdapter by lazy {
        GalleryItemAdapter(needCamera)
    }

    private val albumMediaCollection = AlbumMediaCollection()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        layoutInflater.inflate(R.layout.fragment_draggable_recycler_view, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        rv.layoutManager = GridLayoutManager(context, COLUMN)
        rv.addItemDecoration(StickerSpacingItemDecoration(COLUMN, padding, true))
        adapter.size = (context!!.realSize().x - (COLUMN + 1) * padding) / COLUMN
        rv.adapter = adapter
        adapter.listener = object : GalleryCallback {
            override fun onItemClick(pos: Int, uri: Uri, isVideo: Boolean) {
                callback?.onItemClick(pos, uri, isVideo)
            }

            override fun onCameraClick() {
                callback?.onCameraClick()
            }
        }
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (rv.scrollState != SCROLL_STATE_IDLE) {
                    adapter.hideBLur()
                }
            }
        })
        rv.callback = object : DraggableRecyclerView.Callback {
            override fun onScroll(dis: Float) {
                rvCallback?.onScroll(dis)
            }

            override fun onRelease(fling: Int) {
                rvCallback?.onRelease(fling)
            }
        }

        albumMediaCollection.onCreate(this, this)
        albumMediaCollection.load(album)

        disposable = RxBus.listen(DragReleaseEvent::class.java)
            .subscribe {
                rv.direction = if (it.isExpand) DIRECTION_TOP_2_BOTTOM else DIRECTION_NONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable?.dispose()
        albumMediaCollection.onDestroy()
    }

    private var first = true

    override fun onAlbumMediaLoad(cursor: Cursor) {
        if (!first) return

        first = false
        rv.post {
            val itemList = arrayListOf<Item>()
            while (cursor.moveToNext()) {
                val item = Item.valueOf(cursor)
                itemList.add(item)
            }

            adapter.items = itemList
        }
    }

    override fun onAlbumMediaReset() {}

    fun hideBlur() {
        adapter.hideBLur()
    }
}