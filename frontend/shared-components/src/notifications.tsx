import { useEffect } from "react";
import { create } from "zustand";

interface Notice {
  id: number;
  message: string;
  tone: "success" | "error" | "info";
}
interface NoticeState {
  notices: Notice[];
  push(message: string, tone?: Notice["tone"]): void;
  dismiss(id: number): void;
}

export const useNoticeStore = create<NoticeState>((set) => ({
  notices: [],
  push: (message, tone = "success") => {
    const id = Date.now() + Math.random();
    set((state) => ({ notices: [...state.notices, { id, message, tone }] }));
    window.setTimeout(
      () =>
        set((state) => ({
          notices: state.notices.filter((item) => item.id !== id),
        })),
      3600,
    );
  },
  dismiss: (id) =>
    set((state) => ({
      notices: state.notices.filter((item) => item.id !== id),
    })),
}));

export const notify = (message: string, tone: Notice["tone"] = "success") =>
  useNoticeStore.getState().push(message, tone);

export function ToastRegion() {
  const notices = useNoticeStore((state) => state.notices);
  const dismiss = useNoticeStore((state) => state.dismiss);
  useEffect(() => {
    if (!notices.length) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape")
        notices.forEach((notice) => dismiss(notice.id));
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [notices, dismiss]);
  return (
    <div className="toast-region" aria-live="polite">
      {notices.map((notice) => (
        <div
          key={notice.id}
          className={`toast toast-${notice.tone}`}
          role={notice.tone === "error" ? "alert" : "status"}
        >
          <span>
            {notice.tone === "success"
              ? "✓"
              : notice.tone === "error"
                ? "!"
                : "i"}
          </span>
          {notice.message}
          <button
            aria-label="Dismiss notification"
            onClick={() => dismiss(notice.id)}
          >
            ×
          </button>
        </div>
      ))}
    </div>
  );
}
