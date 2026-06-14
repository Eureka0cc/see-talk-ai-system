import { useCallback, useEffect, useRef, useState } from "react";

const SILENCE_THRESHOLD = 0.015;
const SPEECH_THRESHOLD = 0.03;
const SILENCE_DURATION_MS = 1200;

interface UseVoiceActivityOptions {
  onSpeechEnd?: (transcript: string) => void;
}

export function useVoiceActivity(options: UseVoiceActivityOptions = {}) {
  const [isListening, setIsListening] = useState(false);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [transcript, setTranscript] = useState("");
  const [error, setError] = useState<string | null>(null);

  const streamRef = useRef<MediaStream | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const rafRef = useRef<number>(0);
  const recognitionRef = useRef<SpeechRecognition | null>(null);
  const silenceStartRef = useRef<number | null>(null);
  const speakingRef = useRef(false);
  const transcriptRef = useRef("");
  const onSpeechEndRef = useRef(options.onSpeechEnd);
  onSpeechEndRef.current = options.onSpeechEnd;

  const stop = useCallback(() => {
    cancelAnimationFrame(rafRef.current);
    recognitionRef.current?.stop();
    streamRef.current?.getTracks().forEach((t) => t.stop());
    audioCtxRef.current?.close();
    streamRef.current = null;
    analyserRef.current = null;
    audioCtxRef.current = null;
    speakingRef.current = false;
    setIsListening(false);
    setIsSpeaking(false);
  }, []);

  const start = useCallback(async (): Promise<boolean> => {
    try {
      setError(null);
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;

      const audioCtx = new AudioContext();
      audioCtxRef.current = audioCtx;
      const source = audioCtx.createMediaStreamSource(stream);
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 512;
      source.connect(analyser);
      analyserRef.current = analyser;

      const SpeechRecognitionCtor =
        window.SpeechRecognition || window.webkitSpeechRecognition;
      if (SpeechRecognitionCtor) {
        const recognition = new SpeechRecognitionCtor();
        recognition.lang = "zh-CN";
        recognition.continuous = true;
        recognition.interimResults = true;
        recognitionRef.current = recognition;

        recognition.onresult = (event: SpeechRecognitionEvent) => {
          let interim = "";
          let final = "";
          for (let i = event.resultIndex; i < event.results.length; i++) {
            const result = event.results[i];
            if (result.isFinal) {
              final += result[0].transcript;
            } else {
              interim += result[0].transcript;
            }
          }
          const text = final || interim;
          transcriptRef.current = text;
          setTranscript(text);
        };

        recognition.onerror = (ev: SpeechRecognitionErrorEvent) => {
          if (ev.error !== "no-speech" && ev.error !== "aborted") {
            setError(`语音识别错误: ${ev.error}`);
          }
        };

        recognition.start();
      } else {
        setError("浏览器不支持 Web Speech API，请使用 Chrome 浏览器");
      }

      const dataArray = new Float32Array(analyser.fftSize);
      const checkLevel = () => {
        if (!analyserRef.current) return;
        analyserRef.current.getFloatTimeDomainData(dataArray);
        let sum = 0;
        for (let i = 0; i < dataArray.length; i++) {
          sum += dataArray[i] * dataArray[i];
        }
        const rms = Math.sqrt(sum / dataArray.length);

        if (rms > SPEECH_THRESHOLD) {
          speakingRef.current = true;
          setIsSpeaking(true);
          silenceStartRef.current = null;
        } else if (rms < SILENCE_THRESHOLD) {
          if (speakingRef.current && silenceStartRef.current === null) {
            silenceStartRef.current = Date.now();
          }
          if (
            silenceStartRef.current &&
            Date.now() - silenceStartRef.current > SILENCE_DURATION_MS
          ) {
            speakingRef.current = false;
            setIsSpeaking(false);
            const text = transcriptRef.current.trim();
            if (text) {
              onSpeechEndRef.current?.(text);
              transcriptRef.current = "";
              setTranscript("");
            }
            silenceStartRef.current = null;
          }
        }
        rafRef.current = requestAnimationFrame(checkLevel);
      };

      setIsListening(true);
      rafRef.current = requestAnimationFrame(checkLevel);
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : "无法访问麦克风");
      return false;
    }
  }, []);

  useEffect(() => () => stop(), [stop]);

  return { isListening, isSpeaking, transcript, error, start, stop };
}
