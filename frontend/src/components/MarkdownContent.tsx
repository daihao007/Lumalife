import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

type MarkdownContentProps = {
  content: string;
  inline?: boolean;
};

/** Renders model/user text as Markdown without allowing raw HTML. */
export default function MarkdownContent({ content, inline = false }: MarkdownContentProps) {
  if (inline) {
    return <span className="markdown-preview">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{ p: ({ children }) => <>{children}</> }}
      >
        {content}
      </ReactMarkdown>
    </span>;
  }

  return <div className="markdown-content">
    <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
  </div>;
}
