import { statusLabel } from "../utils";

export default function StatusTimeline({ timeline }: { timeline?: Record<string, string> }) {
  const entries = Object.entries(timeline || {});
  if (!entries.length) return null;
  return <div className="timeline">{entries.map(([status, time]) => <span key={status}><i />{statusLabel(status)}<small>{new Date(time).toLocaleString()}</small></span>)}</div>;
}
