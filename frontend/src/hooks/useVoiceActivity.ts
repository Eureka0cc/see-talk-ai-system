import { useCallback, useEffect, useRef, useState } from "react";

const SILENCE_THRESHOLD = 0.015;
const SPEECH_THRESHOLD = 0.03;
const SILENCE_DURATION_MS = 120;
const INTERRUPT_MIN_DURATION_MS = 400;
const INTERRUPT_RMS_THRESHOLD = 0.05;
const VOICE_BAND_START_BIN = 1;
const VOICE_BAND_END_BIN = 5;
const VOICE_ENERGY_RATIO_MIN = 0.35;
const RESTART_WINDOW_MS = 10000;
const MAX_RESTARTS_IN_WINDOW = 3;

function isHumanVoice(analyser: AnalyserNode, freqData: Uint8Array): boolean {
  analyser.getByteFrequencyData(freqData);

  let voiceBandEnergy = 0;
  let totalEnergy = 0;
  for (let i = 0; i < freqData.length; i++) {
    const value = freqData[i];
    totalEnergy += value;
    if (i >= VOICE_BAND_START_BIN && i <= VOICE_BAND_END_BIN) {
      voiceBandEnergy += value;
    }
  }

  if (totalEnergy < 10) return false;
  return voiceBandEnergy / totalEnergy > VOICE_ENERGY_RATIO_MIN;
}

interface UseVoiceActivityOptions {
  onSpeechStart?: () => void;
  onSpeechEnd?: (transcript: string) => void;
}

export function useVoiceActivity(options: UseVoiceActivityOptions = {}) {
  const [isListening, setIsListening] = useState(false);
  const [isMicEnabled, setIsMicEnabledState] = useState(true);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [transcript, setTranscript] = useState("");
  const [error, setError] = useState<string | null>(null);

  const streamRef = useRef<MediaStream | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const rafRef = useRef<number>(0);
  const recognitionRef = useRef<SpeechRecognition | null>(null);
  const intentionalStopRef = useRef(false);
  const isMicEnabledRef = useRef(true);
  const micPausedRef = useRef(false);
  const suppressRecognitionRef = useRef(false);
  const recognitionRestartCountRef = useRef(0);
  const recognitionRestartWindowStartRef = useRef<number | null>(null);
  const startRecognitionRef = useRef<() => void>(() => {});
  const silenceStartRef = useRef<number | null>(null);
  const speechStartTimeRef = useRef<number | null>(null);
  const interruptFiredRef = useRef(false);
  const speakingRef = useRef(false);
  const transcriptRef = useRef("");
  const dataArrayRef = useRef<Float32Array | null>(null);
  const freqDataRef = useRef<Uint8Array | null>(null);
  const onSpeechStartRef = useRef(options.onSpeechStart);
  const onSpeechEndRef = useRef(options.onSpeechEnd);
  onSpeechStartRef.current = options.onSpeechStart;
  onSpeechEndRef.current = options.onSpeechEnd;

  const stopVadLoop = useCallback(() => {
    cancelAnimationFrame(rafRef.current);
    rafRef.current = 0;
  }, []);

  const stopRecognition = useCallback(() => {
    const recognition = recognitionRef.current;
    if (!recognition) return;
    intentionalStopRef.current = true;
    recognitionRef.current = null;
    try {
      recognition.stop();
    } catch {
      // Ignore stop errors for already-stopped instances.
    }
  }, []);

  const clearSpeechState = useCallback(() => {
    speakingRef.current = false;
    silenceStartRef.current = null;
    speechStartTimeRef.current = null;
    interruptFiredRef.current = false;
    transcriptRef.current = "";
    setIsSpeaking(false);
    setTranscript("");
  }, []);

  const startRecognition = useCallback(() => {
    if (!streamRef.current || !isMicEnabledRef.current || micPausedRef.current) return;
    if (recognitionRef.current) return;

    const SpeechRecognitionCtor =
      window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognitionCtor) return;

    const recognition = new SpeechRecognitionCtor();
    recognition.lang = "zh-CN";
    recognition.continuous = true;
    recognition.interimResults = true;
    recognitionRef.current = recognition;

    recognition.onresult = (event: SpeechRecognitionEvent) => {
      if (recognitionRef.current !== recognition) return;
      if (suppressRecognitionRef.current) return;
      recognitionRestartCountRef.current = 0;
      recognitionRestartWindowStartRef.current = null;
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
      if (recognitionRef.current !== recognition) return;
      if (ev.error === "aborted") return;
      if (ev.error === "no-speech" || ev.error === "network") {
        if (!micPausedRef.current && isMicEnabledRef.current && streamRef.current) {
          try {
            recognition.stop();
          } catch {
            // Ignore stop errors and rely on onend auto recovery.
          }
        }
        return;
      }
      setError(`语音识别错误: ${ev.error}`);
    };

    recognition.onend = () => {
      if (recognitionRef.current === recognition) {
        recognitionRef.current = null;
      }
      const wasIntentionalStop = intentionalStopRef.current;
      intentionalStopRef.current = false;
      if (
        wasIntentionalStop ||
        !streamRef.current ||
        !isMicEnabledRef.current ||
        micPausedRef.current
      ) {
        return;
      }

      const now = Date.now();
      const windowStart = recognitionRestartWindowStartRef.current;
      if (!windowStart || now - windowStart > RESTART_WINDOW_MS) {
        recognitionRestartWindowStartRef.current = now;
        recognitionRestartCountRef.current = 1;
      } else {
        recognitionRestartCountRef.current += 1;
      }
      if (recognitionRestartCountRef.current > MAX_RESTARTS_IN_WINDOW) {
        setError("语音识别服务异常，请刷新页面后重试");
        return;
      }
      startRecognitionRef.current();
    };

    intentionalStopRef.current = false;
    try {
      recognition.start();
    } catch (e) {
      setError(e instanceof Error ? e.message : "语音识别启动失败");
    }
  }, [stopRecognition]);

  useEffect(() => {
    startRecognitionRef.current = startRecognition;
  }, [startRecognition]);

  const startVadLoop = useCallback(() => {
    const analyser = analyserRef.current;
    if (!analyser) return;

    if (!dataArrayRef.current || dataArrayRef.current.length !== analyser.fftSize) {
      dataArrayRef.current = new Float32Array(analyser.fftSize);
    }
    const dataArray = dataArrayRef.current;

    const checkLevel = () => {
      if (!analyserRef.current) return;
      analyserRef.current.getFloatTimeDomainData(dataArray as Float32Array<ArrayBuffer>);
      let sum = 0;
      for (let i = 0; i < dataArray.length; i++) {
        sum += dataArray[i] * dataArray[i];
      }
      const rms = Math.sqrt(sum / dataArray.length);
      const now = Date.now();
      if (rms > SPEECH_THRESHOLD) {
        if (speechStartTimeRef.current === null) {
          speechStartTimeRef.current = now;
          interruptFiredRef.current = false;
        }
        if (!speakingRef.current) {
          speakingRef.current = true;
          setIsSpeaking(true);
        }
        silenceStartRef.current = null;

        const analyserNode = analyserRef.current;
        const freqData = freqDataRef.current;
        const shouldCheckVoice =
          speechStartTimeRef.current !== null &&
          now - speechStartTimeRef.current > INTERRUPT_MIN_DURATION_MS &&
          rms > INTERRUPT_RMS_THRESHOLD &&
          !interruptFiredRef.current &&
          analyserNode !== null &&
          freqData !== null;

        const voiceConfirmed =
          shouldCheckVoice && analyserNode && freqData
            ? isHumanVoice(analyserNode, freqData)
            : false;

        if (shouldCheckVoice && voiceConfirmed) {
          interruptFiredRef.current = true;
          onSpeechStartRef.current?.();
          speechStartTimeRef.current = null;
        }
      } else if (rms < SILENCE_THRESHOLD) {
        speechStartTimeRef.current = null;
        interruptFiredRef.current = false;
        if (speakingRef.current && silenceStartRef.current === null) {
          silenceStartRef.current = now;
        }
        if (
          silenceStartRef.current &&
          now - silenceStartRef.current > SILENCE_DURATION_MS
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
      } else {
        speechStartTimeRef.current = null;
      }
      rafRef.current = requestAnimationFrame(checkLevel);
    };

    stopVadLoop();
    rafRef.current = requestAnimationFrame(checkLevel);
  }, [stopVadLoop]);

  const pauseMic = useCallback(() => {
    micPausedRef.current = true;
    stopVadLoop();
    stopRecognition();
    streamRef.current?.getAudioTracks().forEach((track) => {
      track.enabled = false;
    });
    clearSpeechState();
  }, [clearSpeechState, stopRecognition, stopVadLoop]);

  const resumeMic = useCallback(async () => {
    micPausedRef.current = false;
    streamRef.current?.getAudioTracks().forEach((track) => {
      track.enabled = true;
    });
    if (audioCtxRef.current?.state === "suspended") {
      try {
        await audioCtxRef.current.resume();
      } catch {
        setError("音频上下文恢复失败，请重启会话");
        return;
      }
    }
    startRecognition();
    startVadLoop();
  }, [startRecognition, startVadLoop]);

  const setRecognitionSuppressed = useCallback((enabled: boolean) => {
    suppressRecognitionRef.current = enabled;
    if (enabled) {
      transcriptRef.current = "";
      setTranscript("");
      speakingRef.current = false;
      silenceStartRef.current = null;
      speechStartTimeRef.current = null;
      interruptFiredRef.current = false;
      setIsSpeaking(false);
    } else {
      silenceStartRef.current = null;
      speakingRef.current = false;
      speechStartTimeRef.current = null;
      interruptFiredRef.current = false;
      setIsSpeaking(false);
    }
  }, []);

  const setMicEnabled = useCallback(
    (enabled: boolean) => {
      setIsMicEnabledState(enabled);
      isMicEnabledRef.current = enabled;
      if (!streamRef.current) return;
      if (enabled) {
        void (async () => {
          await resumeMic();
        })();
      } else {
        pauseMic();
      }
    },
    [pauseMic, resumeMic],
  );

  const stop = useCallback(() => {
    stopVadLoop();
    stopRecognition();
    streamRef.current?.getTracks().forEach((track) => track.stop());
    audioCtxRef.current?.close();
    streamRef.current = null;
    analyserRef.current = null;
    audioCtxRef.current = null;
    dataArrayRef.current = null;
    freqDataRef.current = null;
    micPausedRef.current = false;
    suppressRecognitionRef.current = false;
    recognitionRestartCountRef.current = 0;
    recognitionRestartWindowStartRef.current = null;
    speakingRef.current = false;
    silenceStartRef.current = null;
    speechStartTimeRef.current = null;
    interruptFiredRef.current = false;
    transcriptRef.current = "";
    setIsListening(false);
    setIsSpeaking(false);
    setTranscript("");
    setIsMicEnabledState(true);
    isMicEnabledRef.current = true;
  }, [stopRecognition, stopVadLoop]);

  const start = useCallback(async (): Promise<boolean> => {
    try {
      setError(null);
      setIsMicEnabledState(true);
      isMicEnabledRef.current = true;
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: { ideal: true },
          noiseSuppression: { ideal: true },
          autoGainControl: { ideal: true },
        },
      });
      streamRef.current = stream;
      micPausedRef.current = false;
      suppressRecognitionRef.current = false;
      recognitionRestartCountRef.current = 0;
      recognitionRestartWindowStartRef.current = null;
      speechStartTimeRef.current = null;
      interruptFiredRef.current = false;

      const audioCtx = new AudioContext();
      audioCtxRef.current = audioCtx;
      const source = audioCtx.createMediaStreamSource(stream);
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 512;
      freqDataRef.current = new Uint8Array(analyser.frequencyBinCount);
      source.connect(analyser);
      analyserRef.current = analyser;

      const SpeechRecognitionCtor =
        window.SpeechRecognition || window.webkitSpeechRecognition;
      if (!SpeechRecognitionCtor) {
        setError("浏览器不支持 Web Speech API，请使用 Chrome 浏览器");
      } else {
        startRecognition();
      }

      setIsListening(true);
      startVadLoop();
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : "无法访问麦克风");
      return false;
    }
  }, [startRecognition, startVadLoop]);

  useEffect(() => () => stop(), [stop]);

  return {
    isListening,
    isMicEnabled,
    isSpeaking,
    transcript,
    error,
    start,
    stop,
    setMicEnabled,
    setRecognitionSuppressed,
  };
}
