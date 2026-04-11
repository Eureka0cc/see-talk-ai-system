import { useCallback, useRef, useState } from "react";

function normalizeSpeechText(text: string) {
  return text
    .replace(/!\[([^\]]*)]\([^)]+\)/g, "$1")
    .replace(/\[([^\]]+)]\([^)]+\)/g, "$1")
    .replace(/[`*_~>#-]/g, "")
    .replace(/[\p{Extended_Pictographic}\uFE0F\u200D]/gu, "")
    .replace(/\s+/g, " ")
    .trim();
}

interface UseSpeechSynthesisOptions {
  onStart?: () => void;
  onEnd?: () => void;
}

export function useSpeechSynthesis(options: UseSpeechSynthesisOptions = {}) {
  const [isSpeaking, setIsSpeaking] = useState(false);
  const onStartRef = useRef(options.onStart);
  const onEndRef = useRef(options.onEnd);
  const stoppedRef = useRef(false);
  const ttsStartTimeRef = useRef(0);
  onStartRef.current = options.onStart;
  onEndRef.current = options.onEnd;

  const speak = useCallback((text: string) => {
    if (!window.speechSynthesis) return;
    const speechText = normalizeSpeechText(text);
    if (!speechText) return;

    stoppedRef.current = true;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(speechText);
    utterance.lang = "zh-CN";
    utterance.onstart = () => {
      ttsStartTimeRef.current = Date.now();
      setIsSpeaking(true);
      onStartRef.current?.();
    };
    utterance.onend = () => {
      setIsSpeaking(false);
      if (!stoppedRef.current) {
        onEndRef.current?.();
      }
    };
    utterance.onerror = () => {
      setIsSpeaking(false);
      if (!stoppedRef.current) {
        onEndRef.current?.();
      }
    };
    window.speechSynthesis.speak(utterance);
    Promise.resolve().then(() => {
      stoppedRef.current = false;
    });
  }, []);

  const stop = useCallback(() => {
    stoppedRef.current = true;
    window.speechSynthesis?.cancel();
    setIsSpeaking(false);
  }, []);

  return { isSpeaking, speak, stop, ttsStartTimeRef };
}
