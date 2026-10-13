import { useCallback, useEffect, useState } from 'react'
import { api, clearCredentials, setCredentials } from './api.js'

const emptyPost = {
  title: '',
  content: '',
  categoryId: '',
  itemType: 'LOST',
  location: '',
  contactInfo: '',
  occurredAt: '',
}

function formatDate(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function getNowForDateTimeLocal() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 16)
}

function ReturnRequestStatusBadge({ status }) {
  const map = {
    PENDING: { label: 'Đang chờ duyệt', className: 'pending' },
    APPROVED: { label: 'Đã chấp nhận', className: 'approved' },
    REJECTED: { label: 'Đã từ chối', className: 'rejected' },
    COMPLETED: { label: 'Đã trao trả', className: 'completed' },
  }
  const item = map[status] || { label: status, className: '' }
  return <span className={`tag ${item.className}`}>{item.label}</span>
}

function App() {
  const [user, setUser] = useState(null)
  const [categories, setCategories] = useState([])
  const [posts, setPosts] = useState([])
  const [selectedPost, setSelectedPost] = useState(null)
  const [filters, setFilters] = useState({ keyword: '', categoryId: '', itemType: '' })
  const [dialog, setDialog] = useState(null)
  const [notice, setNotice] = useState('')
  const [view, setView] = useState('posts') // 'posts' | 'my-requests'

  const loadPosts = useCallback(async () => {
    const query = new URLSearchParams()
    if (filters.keyword) query.set('keyword', filters.keyword)
    if (filters.categoryId) query.set('categoryId', filters.categoryId)
    if (filters.itemType) query.set('itemType', filters.itemType)
    try {
      const page = await api(`/posts?${query}`)
      setPosts(page.content)
    } catch (error) {
      setNotice(error.message)
    }
  }, [filters])

  useEffect(() => {
    api('/categories').then(setCategories).catch((error) => setNotice(error.message))
    api('/auth/me').then(setUser).catch(() => clearCredentials())
  }, [])

  useEffect(() => {
    const query = new URLSearchParams()
    if (filters.keyword) query.set('keyword', filters.keyword)
    if (filters.categoryId) query.set('categoryId', filters.categoryId)
    if (filters.itemType) query.set('itemType', filters.itemType)
    let active = true
    api(`/posts?${query}`)
      .then((page) => { if (active) setPosts(page.content) })
      .catch((error) => { if (active) setNotice(error.message) })
    return () => { active = false }
  }, [filters])

  async function openPost(id) {
    try {
      const postData = await api(`/posts/${id}`)
      setSelectedPost(postData)
      setView('posts')
    } catch (error) {
      setNotice(error.message)
    }
  }

  function logout() {
    clearCredentials()
    setUser(null)
    setSelectedPost(null)
    setView('posts')
    setNotice('Đã đăng xuất')
  }

  async function refreshSelected() {
    if (selectedPost) await openPost(selectedPost.id)
    await loadPosts()
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <button className="brand" onClick={() => { setSelectedPost(null); setView('posts') }}>
          <span className="brand-mark">C</span>
          <span>Campus Hub</span>
        </button>
        <nav>
          {user ? (
            <>
              <span className="user-name">Xin chào, {user.displayName}</span>
              <button
                className={`button ${view === 'my-requests' ? 'primary' : 'ghost'}`}
                onClick={() => { setSelectedPost(null); setView(view === 'my-requests' ? 'posts' : 'my-requests') }}
              >
                {view === 'my-requests' ? 'Bảng tin' : 'Yêu cầu của tôi'}
              </button>
              <button className="button ghost" onClick={() => setDialog('post')}>Đăng bài</button>
              <button className="button secondary" onClick={logout}>Đăng xuất</button>
            </>
          ) : (
            <button className="button primary" onClick={() => setDialog('login')}>Đăng nhập</button>
          )}
        </nav>
      </header>

      <main>
        {notice && <div className="notice" onClick={() => setNotice('')}>{notice}</div>}

        {view === 'my-requests' ? (
          <MyReturnRequests onOpenPost={openPost} onBack={() => setView('posts')} />
        ) : selectedPost ? (
          <PostDetail
            post={selectedPost}
            user={user}
            onBack={() => setSelectedPost(null)}
            onChanged={refreshSelected}
            onDeleted={() => { setSelectedPost(null); loadPosts() }}
            setNotice={setNotice}
            onRequestLogin={() => { setNotice('Vui lòng đăng nhập để gửi yêu cầu nhận lại đồ'); setDialog('login') }}
          />
        ) : (
          <>
            <section className="hero">
              <div>
                <p className="eyebrow">Không gian chung của sinh viên</p>
                <h1>Chia sẻ điều đang diễn ra trong trường.</h1>
                <p>Tìm kiếm và trả đồ thất lạc, cập nhật sự kiện, hỏi đáp học tập và cùng giải quyết những vấn đề quanh bạn.</p>
              </div>
              <div className="hero-stat"><strong>{posts.length}</strong><span>bài đăng đang hiển thị</span></div>
            </section>

            <section className="filters" aria-label="Tìm kiếm bài đăng">
              <input value={filters.keyword} placeholder="Tìm theo tiêu đề, nội dung hoặc địa điểm..."
                onChange={(event) => setFilters({ ...filters, keyword: event.target.value })} />
              <select value={filters.categoryId}
                onChange={(event) => setFilters({ ...filters, categoryId: event.target.value })}
                aria-label="Chọn chuyên mục">
                <option value="">Tất cả chuyên mục</option>
                {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
              </select>
              <select value={filters.itemType}
                onChange={(event) => setFilters({ ...filters, itemType: event.target.value })}
                aria-label="Lọc theo loại tin">
                <option value="">Tất cả loại tin</option>
                <option value="LOST">Mất đồ (LOST)</option>
                <option value="FOUND">Nhặt được (FOUND)</option>
              </select>
            </section>

            <section className="post-grid">
              {posts.length > 0 ? (
                posts.map((post) => <PostCard key={post.id} post={post} onOpen={openPost} />)
              ) : (
                <div className="empty">Chưa có bài đăng phù hợp.</div>
              )}
            </section>
          </>
        )}
      </main>

      <footer>Campus Hub · Hệ thống diễn đàn & Đồ thất lạc sinh viên</footer>

      {dialog === 'login' && <AuthDialog onClose={() => setDialog(null)} onLogin={(loggedInUser) => {
        setUser(loggedInUser); setDialog(null); setNotice('Đăng nhập thành công'); loadPosts()
      }} />}
      {dialog === 'post' && <PostDialog categories={categories} onClose={() => setDialog(null)}
        onSaved={() => { setDialog(null); setNotice('Đã đăng bài'); loadPosts() }} />}
    </div>
  )
}

function PostCard({ post, onOpen }) {
  const isLost = post.itemType === 'LOST'
  const isFound = post.itemType === 'FOUND'
  const isResolved = post.status === 'RESOLVED'

  return (
    <article className="post-card" onClick={() => onOpen(post.id)}>
      <div className="card-top">
        <div className="card-badges">
          <span className="tag">{post.category.name}</span>
          {isLost && <span className="tag lost">MẤT ĐỒ</span>}
          {isFound && <span className="tag found">NHẶT ĐƯỢC</span>}
          {isResolved && <span className="tag resolved">Đã hoàn tất</span>}
        </div>
        <span>{formatDate(post.createdAt)}</span>
      </div>
      <h2>{post.title}</h2>
      {post.location && (
        <div className="card-location">
          <span>📍 {post.location}</span>
        </div>
      )}
      <p>{post.content}</p>
      <div className="card-footer">
        <span>{post.author.displayName}</span>
        <span>♥ {post.reactionCount} · {post.commentCount} bình luận</span>
      </div>
    </article>
  )
}

function PostDetail({ post, user, onBack, onChanged, onDeleted, setNotice, onRequestLogin }) {
  const [comment, setComment] = useState('')
  const [submittingComment, setSubmittingComment] = useState(false)
  const [showReturnModal, setShowReturnModal] = useState(false)

  const isAuthor = user && user.id === post.author.id
  const isAdmin = user && user.role === 'ADMIN'
  const canManage = isAuthor || isAdmin

  const isLost = post.itemType === 'LOST'
  const isFound = post.itemType === 'FOUND'
  const isResolved = post.status === 'RESOLVED'
  const hasLostFoundInfo = post.itemType || post.location || post.contactInfo || post.occurredAt

  // Điều kiện để hiển thị nút yêu cầu nhận đồ: bài FOUND, đang PUBLISHED, và không phải chính tác giả
  const canRequestReturn = isFound && post.status === 'PUBLISHED' && !isAuthor

  async function act(action, success) {
    try {
      await action()
      setNotice(success)
      await onChanged()
    } catch (error) {
      setNotice(error.message)
    }
  }

  return (
    <section className="detail">
      <button className="back" onClick={onBack}>← Quay lại danh sách</button>
      <div className="detail-card">
        <div className="card-top">
          <div className="card-badges">
            <span className="tag">{post.category.name}</span>
            {isLost && <span className="tag lost">MẤT ĐỒ</span>}
            {isFound && <span className="tag found">NHẶT ĐƯỢC</span>}
            {isResolved && <span className="tag resolved">Đã hoàn tất</span>}
          </div>
          <span>{formatDate(post.createdAt)}</span>
        </div>
        <h1>{post.title}</h1>
        <p className="author">
          Đăng bởi {post.author.displayName} · Trạng thái: {post.status === 'RESOLVED' ? 'Đã hoàn tất' : post.status === 'HIDDEN' ? 'Đã ẩn' : 'Đang hiển thị'}
        </p>

        {hasLostFoundInfo && (
          <div className="lost-found-box">
            {post.itemType && (
              <div className="lost-found-item">
                <strong>Loại tin</strong>
                <span>{isLost ? 'Mất đồ (Cần tìm lại)' : 'Nhặt được (Tìm chủ nhân)'}</span>
              </div>
            )}
            {post.location && (
              <div className="lost-found-item">
                <strong>Địa điểm</strong>
                <span>📍 {post.location}</span>
              </div>
            )}
            {post.occurredAt && (
              <div className="lost-found-item">
                <strong>Thời gian xảy ra</strong>
                <span>🕒 {formatDate(post.occurredAt)}</span>
              </div>
            )}
            {post.contactInfo && (
              <div className="lost-found-item">
                <strong>Thông tin liên hệ</strong>
                <span>📞 {post.contactInfo}</span>
              </div>
            )}
          </div>
        )}

        <div className="content">{post.content}</div>

        <div className="actions">
          <button className={`button ${post.reactedByCurrentUser ? 'primary' : 'secondary'}`} disabled={!user}
            onClick={() => act(() => api(`/posts/${post.id}/reaction`, { method: 'POST' }), 'Đã cập nhật quan tâm')}>
            ♥ {post.reactionCount} quan tâm
          </button>

          {/* Nút yêu cầu nhận lại đồ */}
          {canRequestReturn && (
            user ? (
              <button className="button primary" onClick={() => setShowReturnModal(true)}>
                Yêu cầu nhận lại đồ
              </button>
            ) : (
              <button className="button ghost" onClick={onRequestLogin}>
                Đăng nhập để yêu cầu nhận đồ
              </button>
            )
          )}

          {canManage && post.status !== 'RESOLVED' && <button className="button secondary"
            onClick={() => act(() => api(`/posts/${post.id}/status`, {
              method: 'PATCH', body: JSON.stringify({ status: 'RESOLVED' }),
            }), 'Đã đánh dấu hoàn tất')}>Đánh dấu hoàn tất</button>}

          {isAdmin && post.status !== 'HIDDEN' && <button className="button secondary"
            onClick={() => act(() => api(`/posts/${post.id}/status`, {
              method: 'PATCH', body: JSON.stringify({ status: 'HIDDEN' }),
            }), 'Đã ẩn bài đăng')}>Ẩn bài</button>}

          {canManage && <button className="button danger" onClick={async () => {
            if (!window.confirm('Xóa bài đăng này?')) return
            try {
              await api(`/posts/${post.id}`, { method: 'DELETE' })
              setNotice('Đã xóa bài đăng')
              onDeleted()
            } catch (error) {
              setNotice(error.message)
            }
          }}>Xóa bài</button>}
        </div>
      </div>

      {/* Khu vực quản lý yêu cầu nhận đồ dành cho chủ bài hoặc ADMIN */}
      {canManage && isFound && (
        <ReturnRequestsManager post={post} setNotice={setNotice} onPostUpdated={onChanged} />
      )}

      <div className="comments">
        <h2>Bình luận ({post.commentCount})</h2>
        {user ? <form onSubmit={(event) => {
          event.preventDefault()
          setSubmittingComment(true)
          act(() => api(`/posts/${post.id}/comments`, { method: 'POST', body: JSON.stringify({ content: comment }) }),
            'Đã thêm bình luận').then(() => setComment('')).finally(() => setSubmittingComment(false))
        }}>
          <textarea value={comment} onChange={(event) => setComment(event.target.value)}
            placeholder="Viết bình luận..." required maxLength="1000" disabled={submittingComment} />
          <button className="button primary" disabled={submittingComment}>
            {submittingComment ? 'Đang gửi...' : 'Gửi bình luận'}
          </button>
        </form> : <p className="muted">Đăng nhập để bình luận hoặc bày tỏ quan tâm.</p>}
        <div className="comment-list">
          {post.comments && post.comments.map((item) => <div className="comment" key={item.id}>
            <strong>{item.author.displayName}</strong><span>{formatDate(item.createdAt)}</span><p>{item.content}</p>
          </div>)}
        </div>
      </div>

      {showReturnModal && (
        <ReturnRequestModal
          post={post}
          onClose={() => setShowReturnModal(false)}
          onSuccess={() => {
            setShowReturnModal(false)
            setNotice('Đã gửi yêu cầu nhận lại đồ thành công!')
            onChanged()
          }}
        />
      )}
    </section>
  )
}

function ReturnRequestsManager({ post, setNotice, onPostUpdated }) {
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(true)
  const [processingId, setProcessingId] = useState(null)

  const reloadRequests = useCallback(async () => {
    setLoading(true)
    try {
      const data = await api(`/posts/${post.id}/return-requests`)
      setRequests(data)
    } catch (err) {
      setNotice(err.message)
    } finally {
      setLoading(false)
    }
  }, [post.id, setNotice])

  useEffect(() => {
    let active = true
    api(`/posts/${post.id}/return-requests`)
      .then((data) => { if (active) setRequests(data) })
      .catch((err) => { if (active) setNotice(err.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [post.id, setNotice])

  async function updateStatus(requestId, status, successText) {
    setProcessingId(requestId)
    try {
      await api(`/return-requests/${requestId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      })
      setNotice(successText)
      await reloadRequests()
      await onPostUpdated()
    } catch (err) {
      setNotice(err.message)
    } finally {
      setProcessingId(null)
    }
  }

  return (
    <section className="return-requests-section">
      <h2>Yêu cầu nhận đồ ({requests.length})</h2>
      {loading ? (
        <div className="empty">Đang tải danh sách yêu cầu...</div>
      ) : requests.length > 0 ? (
        <div className="requests-list">
          {requests.map((req) => (
            <div className="request-card" key={req.id}>
              <div className="request-card-header">
                <div>
                  <strong>{req.requester.displayName}</strong>
                  <span className="muted"> (@{req.requester.username})</span>
                  <span className="request-date">· {formatDate(req.createdAt)}</span>
                </div>
                <ReturnRequestStatusBadge status={req.status} />
              </div>
              <p className="request-msg">{req.verificationMessage}</p>
              <div className="request-actions">
                {req.status === 'PENDING' && (
                  <>
                    <button
                      className="button primary small"
                      disabled={processingId === req.id}
                      onClick={() => updateStatus(req.id, 'APPROVED', 'Đã chấp nhận yêu cầu')}
                    >
                      {processingId === req.id ? 'Đang xử lý...' : 'Chấp nhận'}
                    </button>
                    <button
                      className="button danger small"
                      disabled={processingId === req.id}
                      onClick={() => updateStatus(req.id, 'REJECTED', 'Đã từ chối yêu cầu')}
                    >
                      {processingId === req.id ? 'Đang xử lý...' : 'Từ chối'}
                    </button>
                  </>
                )}
                {req.status === 'APPROVED' && (
                  <button
                    className="button primary small"
                    disabled={processingId === req.id}
                    onClick={() => updateStatus(req.id, 'COMPLETED', 'Đã xác nhận trao trả đồ thành công!')}
                  >
                    {processingId === req.id ? 'Đang xử lý...' : 'Xác nhận đã trao trả'}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="empty">Chưa có ai gửi yêu cầu nhận lại đồ này.</div>
      )}
    </section>
  )
}

function ReturnRequestModal({ post, onClose, onSuccess }) {
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    const trimmed = message.trim()
    if (trimmed.length < 10) {
      setError('Lời nhắn xác minh cần tối thiểu 10 ký tự')
      return
    }
    if (trimmed.length > 2000) {
      setError('Lời nhắn xác minh tối đa 2000 ký tự')
      return
    }

    setSubmitting(true)
    try {
      await api(`/posts/${post.id}/return-requests`, {
        method: 'POST',
        body: JSON.stringify({ verificationMessage: trimmed }),
      })
      onSuccess()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog title="Yêu cầu nhận lại đồ" onClose={onClose}>
      <form className="form" onSubmit={handleSubmit}>
        {error && <p className="form-error">{error}</p>}
        <p className="muted" style={{ margin: 0 }}>
          Bài đăng: <strong>{post.title}</strong>
        </p>
        <label>
          Lời nhắn xác minh sở hữu
          <textarea
            required
            rows="6"
            maxLength="2000"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            disabled={submitting}
            placeholder="Mô tả chi tiết đặc điểm nhận dạng của món đồ (màu sắc, vết xước, nội dung bên trong...) để người nhặt xác minh..."
          />
          <span className="form-hint">Tối thiểu 10 ký tự, tối đa 2000 ký tự. Thông tin này chỉ người đăng tin mới xem được.</span>
        </label>
        <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end', marginTop: '10px' }}>
          <button type="button" className="button secondary" onClick={onClose} disabled={submitting}>
            Hủy
          </button>
          <button type="submit" className="button primary" disabled={submitting}>
            {submitting ? 'Đang gửi...' : 'Gửi yêu cầu'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}

function MyReturnRequests({ onOpenPost, onBack }) {
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    api('/return-requests/mine')
      .then((data) => { if (active) setRequests(data) })
      .catch((err) => { if (active) setError(err.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  return (
    <section className="my-requests-view">
      <button className="back" onClick={onBack}>← Quay lại bảng tin</button>
      <div className="my-requests-header">
        <div>
          <h1>Yêu cầu nhận lại đồ của tôi</h1>
          <span>Danh sách các yêu cầu bạn đã gửi tới người nhặt được đồ</span>
        </div>
        <strong>{requests.length} yêu cầu</strong>
      </div>

      {error && <p className="form-error">{error}</p>}

      {loading ? (
        <div className="empty">Đang tải danh sách yêu cầu...</div>
      ) : requests.length > 0 ? (
        requests.map((req) => (
          <div className="my-request-card" key={req.id}>
            <div className="my-request-top">
              <div>
                <button className="my-request-title" onClick={() => onOpenPost(req.postId)}>
                  {req.post?.title || `Bài đăng #${req.postId}`}
                </button>
                <div className="my-request-meta" style={{ marginTop: '6px' }}>
                  {req.post?.location && <span>📍 {req.post.location}</span>}
                  <span>Gửi ngày: {formatDate(req.createdAt)}</span>
                </div>
              </div>
              <ReturnRequestStatusBadge status={req.status} />
            </div>

            <div style={{ background: '#fafbfa', padding: '12px 14px', borderRadius: '10px', border: '1px solid #edf0ed' }}>
              <div style={{ fontSize: '12px', fontWeight: '600', color: 'var(--muted)', marginBottom: '4px' }}>
                LỜI NHẮN XÁC MINH CỦA BẠN:
              </div>
              <p style={{ margin: 0, lineHeight: 1.6, color: '#17201c', whiteSpace: 'pre-wrap' }}>
                {req.verificationMessage}
              </p>
            </div>
          </div>
        ))
      ) : (
        <div className="empty">Bạn chưa gửi yêu cầu nhận lại đồ nào.</div>
      )}
    </section>
  )
}

function AuthDialog({ onClose, onLogin }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ username: '', displayName: '', password: '' })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      if (mode === 'register') {
        await api('/auth/register', { method: 'POST', body: JSON.stringify(form) })
      }
      setCredentials(form.username, form.password)
      onLogin(await api('/auth/me'))
    } catch (requestError) {
      clearCredentials()
      setError(requestError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return <Dialog title={mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'} onClose={onClose}>
    <form className="form" onSubmit={submit}>
      {error && <p className="form-error">{error}</p>}
      <label>Tên đăng nhập<input required minLength="3" value={form.username}
        onChange={(event) => setForm({ ...form, username: event.target.value })} disabled={submitting} /></label>
      {mode === 'register' && <label>Tên hiển thị<input required value={form.displayName}
        onChange={(event) => setForm({ ...form, displayName: event.target.value })} disabled={submitting} /></label>}
      <label>Mật khẩu<input required minLength="6" type="password" value={form.password}
        onChange={(event) => setForm({ ...form, password: event.target.value })} disabled={submitting} /></label>
      <button className="button primary" disabled={submitting}>
        {submitting ? 'Đang xử lý...' : mode === 'login' ? 'Đăng nhập' : 'Đăng ký'}
      </button>
      <button type="button" className="text-button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }}>
        {mode === 'login' ? 'Chưa có tài khoản? Đăng ký' : 'Đã có tài khoản? Đăng nhập'}
      </button>
    </form>
  </Dialog>
}

function PostDialog({ categories, onClose, onSaved }) {
  const [form, setForm] = useState(emptyPost)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const selectedCategory = categories.find((c) => String(c.id) === String(form.categoryId))
  const isLostAndFound = selectedCategory?.slug === 'do-that-lac'

  async function submit(event) {
    event.preventDefault()
    setError('')
    setSubmitting(true)

    try {
      const payload = {
        title: form.title.trim(),
        content: form.content.trim(),
        categoryId: Number(form.categoryId),
      }

      if (isLostAndFound) {
        if (!form.itemType) {
          setError('Vui lòng chọn loại tin (Mất đồ hoặc Nhặt được)')
          setSubmitting(false)
          return
        }
        if (!form.location.trim()) {
          setError('Vui lòng nhập địa điểm')
          setSubmitting(false)
          return
        }
        if (!form.contactInfo.trim()) {
          setError('Vui lòng nhập thông tin liên hệ')
          setSubmitting(false)
          return
        }
        if (!form.occurredAt) {
          setError('Vui lòng chọn thời gian xảy ra')
          setSubmitting(false)
          return
        }
        const occurredTime = new Date(form.occurredAt).getTime()
        if (isNaN(occurredTime)) {
          setError('Thời gian không hợp lệ')
          setSubmitting(false)
          return
        }
        if (occurredTime > Date.now()) {
          setError('Thời gian xảy ra không được ở tương lai')
          setSubmitting(false)
          return
        }

        payload.itemType = form.itemType
        payload.location = form.location.trim()
        payload.contactInfo = form.contactInfo.trim()
        payload.occurredAt = new Date(form.occurredAt).toISOString()
      }

      await api('/posts', { method: 'POST', body: JSON.stringify(payload) })
      onSaved()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog title={isLostAndFound ? 'Đăng tin đồ thất lạc' : 'Tạo bài đăng'} onClose={onClose}>
      <form className="form" onSubmit={submit}>
        {error && <p className="form-error">{error}</p>}
        <label>
          Tiêu đề
          <input
            required
            maxLength="160"
            value={form.title}
            placeholder={isLostAndFound ? 'Ví dụ: Mất ví màu đen gần thư viện...' : 'Tiêu đề bài viết...'}
            onChange={(event) => setForm({ ...form, title: event.target.value })}
            disabled={submitting}
          />
        </label>
        <label>
          Chuyên mục
          <select
            required
            value={form.categoryId}
            onChange={(event) => setForm({ ...form, categoryId: event.target.value })}
            disabled={submitting}
          >
            <option value="">Chọn chuyên mục</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>{category.name}</option>
            ))}
          </select>
        </label>

        {isLostAndFound && (
          <>
            <div className="form-row">
              <label>
                Loại tin
                <select
                  required
                  value={form.itemType}
                  onChange={(event) => setForm({ ...form, itemType: event.target.value })}
                  disabled={submitting}
                >
                  <option value="LOST">Mất đồ (LOST) - Cần tìm lại</option>
                  <option value="FOUND">Nhặt được (FOUND) - Tìm chủ nhân</option>
                </select>
              </label>

              <label>
                Địa điểm xảy ra
                <input
                  required
                  maxLength="255"
                  value={form.location}
                  placeholder="Ví dụ: Giảng đường B3, Nhà xe..."
                  onChange={(event) => setForm({ ...form, location: event.target.value })}
                  disabled={submitting}
                />
              </label>
            </div>

            <div className="form-row">
              <label>
                Thời gian xảy ra
                <input
                  required
                  type="datetime-local"
                  max={getNowForDateTimeLocal()}
                  value={form.occurredAt}
                  onChange={(event) => setForm({ ...form, occurredAt: event.target.value })}
                  disabled={submitting}
                />
                <span className="form-hint">Thời gian không được ở tương lai</span>
              </label>

              <label>
                Thông tin liên hệ
                <input
                  required
                  maxLength="255"
                  value={form.contactInfo}
                  placeholder="Ví dụ: SĐT 0912xxx hoặc Zalo..."
                  onChange={(event) => setForm({ ...form, contactInfo: event.target.value })}
                  disabled={submitting}
                />
                <span className="form-hint">Chỉ hiển thị cho người dùng đã đăng nhập</span>
              </label>
            </div>
          </>
        )}

        <label>
          Nội dung chi tiết
          <textarea
            required
            maxLength="10000"
            rows="6"
            value={form.content}
            placeholder={isLostAndFound ? 'Mô tả đặc điểm nhận dạng, hoàn cảnh rơi/nhặt đồ...' : 'Nội dung chi tiết...'}
            onChange={(event) => setForm({ ...form, content: event.target.value })}
            disabled={submitting}
          />
        </label>

        <button className="button primary" disabled={submitting}>
          {submitting ? 'Đang đăng bài...' : isLostAndFound ? 'Đăng tin đồ thất lạc' : 'Đăng bài'}
        </button>
      </form>
    </Dialog>
  )
}

function Dialog({ title, onClose, children }) {
  return (
    <div className="dialog-backdrop" onMouseDown={onClose}>
      <section className="dialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
        <div className="dialog-title">
          <h2>{title}</h2>
          <button onClick={onClose} aria-label="Đóng">×</button>
        </div>
        {children}
      </section>
    </div>
  )
}

export default App
