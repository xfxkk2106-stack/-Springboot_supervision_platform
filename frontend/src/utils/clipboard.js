/**
 * 复制文本到剪贴板，兼容 HTTP 和 HTTPS 环境
 * 优先使用 Clipboard API，失败时回退到 execCommand
 */
export async function copyToClipboard(text) {
  try {
    // 优先使用 Clipboard API（需要安全上下文：HTTPS 或 localhost）
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch (e) {
    // Clipboard API 失败，继续使用回退方案
  }

  // 回退方案：使用 textarea + execCommand
  try {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.left = '-9999px'
    textarea.style.top = '-9999px'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.focus()
    textarea.select()
    const success = document.execCommand('copy')
    document.body.removeChild(textarea)
    return success
  } catch (e) {
    console.error('复制失败:', e)
    return false
  }
}
