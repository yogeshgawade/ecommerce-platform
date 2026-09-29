import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { apiClient, clearSession, readSession } from "./api";
import type { User } from "./types";

interface SessionContextValue {
  user: User | null;
  ready: boolean;
  signIn(email: string, password: string): Promise<User>;
  signUp(email: string, password: string): Promise<User>;
  signOut(): Promise<void>;
}

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const queryClient = useQueryClient();

  useEffect(() => {
    let active = true;
    const expire = () => {
      setUser(null);
      void queryClient.clear();
    };
    window.addEventListener("ecommerce:auth-expired", expire);
    if (!readSession()) setReady(true);
    else
      apiClient
        .me()
        .then((next) => {
          if (active) setUser(next);
        })
        .catch(() => {
          if (active) {
            clearSession();
            setUser(null);
          }
        })
        .finally(() => {
          if (active) setReady(true);
        });
    return () => {
      active = false;
      window.removeEventListener("ecommerce:auth-expired", expire);
    };
  }, [queryClient]);

  const signIn = useCallback(
    async (email: string, password: string) => {
      const next = await apiClient.login(email, password);
      setUser(next);
      await queryClient.invalidateQueries();
      return next;
    },
    [queryClient],
  );
  const signUp = useCallback(
    async (email: string, password: string) => {
      const next = await apiClient.register(email, password);
      setUser(next);
      await queryClient.invalidateQueries();
      return next;
    },
    [queryClient],
  );
  const signOut = useCallback(async () => {
    try {
      await apiClient.logout();
    } finally {
      setUser(null);
      await queryClient.clear();
    }
  }, [queryClient]);

  const value = useMemo(
    () => ({ user, ready, signIn, signUp, signOut }),
    [user, ready, signIn, signUp, signOut],
  );
  return (
    <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
  );
}

export function useSession() {
  const context = useContext(SessionContext);
  if (!context)
    throw new Error("useSession must be used inside SessionProvider");
  return context;
}
