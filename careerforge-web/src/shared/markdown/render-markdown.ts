import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'

const markdown = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
  typographer: false,
})

const defaultLinkOpen =
  markdown.renderer.rules.link_open ??
  ((tokens, index, options, _environment, renderer) => renderer.renderToken(tokens, index, options))

markdown.renderer.rules.link_open = (tokens, index, options, environment, renderer) => {
  const token = tokens[index]
  token?.attrSet('target', '_blank')
  token?.attrSet('rel', 'noopener noreferrer nofollow')
  return defaultLinkOpen(tokens, index, options, environment, renderer)
}

export function renderSafeMarkdown(source: string | undefined) {
  const rendered = markdown.render(source ?? '')
  return DOMPurify.sanitize(rendered, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'iframe', 'object', 'embed', 'form', 'input', 'button'],
    FORBID_ATTR: ['style'],
  })
}
