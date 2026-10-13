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

function App() {
  const [user, setUser] = useState(null)
  const [categories, setCategories] = useState([])
  const [posts, setPosts] = useState([])
  const [selectedPost, setSelectedPost] = useState(null)
  const [filters, setFilters] = useState({ keyword: '', categoryId: '', itemType: '' })
  const [dialog, setDialog] = useState(null)
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(false)

  const loadPosts = useCallback(async () => {
    const query = new URLSearchParams()
    if (filters.keyword) query.set('keyword', filters.keyword)
    if (filters.categoryId) query.set('categoryId', filters.categoryId)
    if (filters.itemType) query.set('itemType', filters.itemType)
    setLoading(true)
    try {
      const page = await api(`/posts?${query}`)
      setPosts(page.content)
    } catch (error) {
      setNotice(error.message)
    } finally {
      setLoading(false)
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
      setSelectedPost(await api(`/posts/${id}`))
    } catch (error) {
      setNotice(error.message)
    }
  }

  function logout() {
    clearCredentials()
    setUser(null)
    setSelectedPost(null)
    setNotice('Đã đăng xuất')
  }

  async function refreshSelected() {
    if (selectedPost) await openPost(selectedPost.id)
    await loadPosts()
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <button className="brand" onClick={() => setSelectedPost(null)}>
          <span className="brand-mark">C</span>
          <span>Campus Hub</span>
        </button>
        <nav>
          {user ? (
            <>
              <span className="user-name">Xin chào, {user.displayName}</span>
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
        {selectedPost ? (
          <PostDetail post={selectedPost} user={user} onBack={() => setSelectedPost(null)}
            onChanged={refreshSelected} onDeleted={() => { setSelectedPost(null); loadPosts() }}
            setNotice={setNotice} />
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
              {loading ? (
                <div className="empty">Đang tải danh sách bài đăng...</div>
              ) : posts.length > 0 ? (
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

function PostDetail({ post, user, onBack, onChanged, onDeleted, setNotice }) {
  const [comment, setComment] = useState('')
  const [submittingComment, setSubmittingComment] = useState(false)
  const canManage = user && (user.id === post.author.id || user.role === 'ADMIN')

  const isLost = post.itemType === 'LOST'
  const isFound = post.itemType === 'FOUND'
  const isResolved = post.status === 'RESOLVED'
  const hasLostFoundInfo = post.itemType || post.location || post.contactInfo || post.occurredAt

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
          {canManage && post.status !== 'RESOLVED' && <button className="button secondary"
            onClick={() => act(() => api(`/posts/${post.id}/status`, {
              method: 'PATCH', body: JSON.stringify({ status: 'RESOLVED' }),
            }), 'Đã đánh dấu hoàn tất')}>Đánh dấu hoàn tất</button>}
          {user?.role === 'ADMIN' && post.status !== 'HIDDEN' && <button className="button secondary"
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
