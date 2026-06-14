import { useEffect, useRef, type RefObject } from "react";
import { FaceLandmarker, FilesetResolver, type FaceLandmarkerResult } from "@mediapipe/tasks-vision";

const WASM_BASE_URL = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@latest/wasm";
const MODEL_URL =
  "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task";

const FACE_OVAL_INDICES = [
  10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378,
  400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109,
];

type Landmark = { x: number; y: number; z: number };

interface CoverRegion {
  sx: number;
  sy: number;
  sw: number;
  sh: number;
}

function computeCover(
  videoWidth: number,
  videoHeight: number,
  canvasWidth: number,
  canvasHeight: number,
): CoverRegion {
  const videoAspect = videoWidth / videoHeight;
  const canvasAspect = canvasWidth / canvasHeight;

  if (videoAspect > canvasAspect) {
    const sh = videoHeight;
    const sw = sh * canvasAspect;
    return { sx: (videoWidth - sw) / 2, sy: 0, sw, sh };
  }

  const sw = videoWidth;
  const sh = sw / canvasAspect;
  return { sx: 0, sy: (videoHeight - sh) / 2, sw, sh };
}

function toCanvasLandmarks(
  normalized: Landmark[],
  cover: CoverRegion,
  videoWidth: number,
  videoHeight: number,
  canvasWidth: number,
  canvasHeight: number,
) {
  return normalized.map((point) => ({
    x: ((point.x * videoWidth - cover.sx) / cover.sw) * canvasWidth,
    y: ((point.y * videoHeight - cover.sy) / cover.sh) * canvasHeight,
  }));
}

function smooth(previous: Array<{ x: number; y: number }> | null, next: Array<{ x: number; y: number }>, alpha = 0.36) {
  if (!previous || previous.length !== next.length) return next.map((point) => ({ ...point }));
  return next.map((point, index) => ({
    x: previous[index].x + alpha * (point.x - previous[index].x),
    y: previous[index].y + alpha * (point.y - previous[index].y),
  }));
}

function getBounds(points: Array<{ x: number; y: number }>) {
  let minX = Number.POSITIVE_INFINITY;
  let minY = Number.POSITIVE_INFINITY;
  let maxX = Number.NEGATIVE_INFINITY;
  let maxY = Number.NEGATIVE_INFINITY;
  for (const point of points) {
    minX = Math.min(minX, point.x);
    minY = Math.min(minY, point.y);
    maxX = Math.max(maxX, point.x);
    maxY = Math.max(maxY, point.y);
  }
  if (!Number.isFinite(minX)) return null;
  return {
    centerX: (minX + maxX) / 2,
    centerY: (minY + maxY) / 2,
    width: maxX - minX,
    height: maxY - minY,
  };
}

function expandFacePoints(points: Array<{ x: number; y: number }>, scale = 1.07) {
  const center = points.reduce(
    (acc, point) => ({ x: acc.x + point.x, y: acc.y + point.y }),
    { x: 0, y: 0 },
  );
  center.x /= points.length;
  center.y /= points.length;
  return points.map((point) => ({
    x: center.x + (point.x - center.x) * scale,
    y: center.y + (point.y - center.y) * scale,
  }));
}

function buildSmoothFacePath(points: Array<{ x: number; y: number }>) {
  const path = new Path2D();
  if (points.length < 3) return path;
  const expanded = expandFacePoints(points, 1.06);
  path.moveTo(expanded[0].x, expanded[0].y);
  for (let i = 1; i < expanded.length; i += 1) {
    const prev = expanded[i - 1];
    const current = expanded[i];
    const midX = (prev.x + current.x) * 0.5;
    const midY = (prev.y + current.y) * 0.5;
    path.quadraticCurveTo(prev.x, prev.y, midX, midY);
  }
  path.quadraticCurveTo(
    expanded[expanded.length - 1].x,
    expanded[expanded.length - 1].y,
    expanded[0].x,
    expanded[0].y,
  );
  path.closePath();
  return path;
}

function ensureNoisePatternCanvas(width: number, height: number) {
  const noiseCanvas = document.createElement("canvas");
  noiseCanvas.width = width;
  noiseCanvas.height = height;
  const noiseCtx = noiseCanvas.getContext("2d");
  if (!noiseCtx) return noiseCanvas;
  const imageData = noiseCtx.createImageData(width, height);
  const { data } = imageData;
  for (let i = 0; i < data.length; i += 4) {
    const base = 215 + Math.floor(Math.random() * 40);
    data[i] = base;
    data[i + 1] = base;
    data[i + 2] = base;
    data[i + 3] = 22 + Math.floor(Math.random() * 18);
  }
  noiseCtx.putImageData(imageData, 0, 0);
  return noiseCanvas;
}

export function useFaceBlur(videoRef: RefObject<HTMLVideoElement | null>, enabled: boolean) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const landmarkerRef = useRef<FaceLandmarker | null>(null);
  const rafRef = useRef<number | null>(null);
  const detectFrameRef = useRef(0);
  const lastFaceRef = useRef<Array<{ x: number; y: number }> | null>(null);
  const sourceCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const noiseCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const mosaicCanvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    let cancelled = false;

    const ensureModel = async () => {
      if (landmarkerRef.current || !enabled) return;
      try {
        const vision = await FilesetResolver.forVisionTasks(WASM_BASE_URL);
        if (cancelled) return;
        landmarkerRef.current = await FaceLandmarker.createFromOptions(vision, {
          baseOptions: {
            modelAssetPath: MODEL_URL,
            delegate: "GPU",
          },
          runningMode: "VIDEO",
          numFaces: 1,
          outputFaceBlendshapes: false,
          outputFacialTransformationMatrixes: false,
        });
      } catch {
        // Keep blur disabled when model loading fails.
      }
    };

    void ensureModel();
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  useEffect(() => {
    const render = (timestamp: number) => {
      const canvas = canvasRef.current;
      const video = videoRef.current;

      if (!canvas || !video || !enabled) {
        if (canvas) {
          canvas.style.display = "none";
          canvas.style.opacity = "0";
          const ctx = canvas.getContext("2d");
          if (ctx) ctx.clearRect(0, 0, canvas.width, canvas.height);
        }
        lastFaceRef.current = null;
        rafRef.current = requestAnimationFrame(render);
        return;
      }

      if (video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA || video.paused || video.ended) {
        rafRef.current = requestAnimationFrame(render);
        return;
      }

      canvas.style.display = "block";
      canvas.style.opacity = "1";
      const width = canvas.clientWidth;
      const height = canvas.clientHeight;
      if (width <= 0 || height <= 0) {
        rafRef.current = requestAnimationFrame(render);
        return;
      }
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
      }

      const ctx = canvas.getContext("2d");
      if (!ctx) {
        rafRef.current = requestAnimationFrame(render);
        return;
      }
      ctx.clearRect(0, 0, width, height);

      const cover = computeCover(video.videoWidth, video.videoHeight, width, height);

      detectFrameRef.current += 1;
      if (detectFrameRef.current % 2 === 1 && landmarkerRef.current) {
        const result: FaceLandmarkerResult = landmarkerRef.current.detectForVideo(video, timestamp);
        const face = result.faceLandmarks[0] as Landmark[] | undefined;
        if (face?.length) {
          const mapped = toCanvasLandmarks(face, cover, video.videoWidth, video.videoHeight, width, height);
          const oval = FACE_OVAL_INDICES.map((index) => mapped[index]).filter(Boolean);
          if (oval.length > 0) {
            lastFaceRef.current = smooth(lastFaceRef.current, oval, 0.4);
          }
        } else {
          lastFaceRef.current = null;
        }
      }

      const currentFace = lastFaceRef.current;
      if (currentFace) {
        const bounds = getBounds(currentFace);
        if (bounds) {
          if (!sourceCanvasRef.current) {
            sourceCanvasRef.current = document.createElement("canvas");
          }
          const sourceCanvas = sourceCanvasRef.current;
          if (sourceCanvas.width !== width || sourceCanvas.height !== height) {
            sourceCanvas.width = width;
            sourceCanvas.height = height;
          }
          const sourceCtx = sourceCanvas.getContext("2d");
          if (sourceCtx) {
            sourceCtx.clearRect(0, 0, width, height);
            sourceCtx.drawImage(video, cover.sx, cover.sy, cover.sw, cover.sh, 0, 0, width, height);
            if (
              !noiseCanvasRef.current ||
              noiseCanvasRef.current.width !== width ||
              noiseCanvasRef.current.height !== height
            ) {
              noiseCanvasRef.current = ensureNoisePatternCanvas(width, height);
            }

            if (!mosaicCanvasRef.current) {
              mosaicCanvasRef.current = document.createElement("canvas");
            }
            const mosaicCanvas = mosaicCanvasRef.current;
            const pixelW = Math.max(32, Math.floor(width / 22));
            const pixelH = Math.max(24, Math.floor(height / 22));
            if (mosaicCanvas.width !== pixelW || mosaicCanvas.height !== pixelH) {
              mosaicCanvas.width = pixelW;
              mosaicCanvas.height = pixelH;
            }
            const mosaicCtx = mosaicCanvas.getContext("2d");

            const facePath = buildSmoothFacePath(currentFace);

            ctx.save();
            ctx.clip(facePath);

            // Pixelated mosaic base.
            if (mosaicCtx) {
              mosaicCtx.clearRect(0, 0, pixelW, pixelH);
              mosaicCtx.drawImage(sourceCanvas, 0, 0, pixelW, pixelH);
              ctx.imageSmoothingEnabled = false;
              ctx.globalAlpha = 0.9;
              ctx.drawImage(mosaicCanvas, 0, 0, pixelW, pixelH, 0, 0, width, height);
              ctx.imageSmoothingEnabled = true;
              ctx.globalAlpha = 1;
            }

            // Frosted blur blend over mosaic.
            ctx.filter = "blur(10px) saturate(0.9)";
            ctx.globalAlpha = 0.55;
            ctx.drawImage(sourceCanvas, 0, 0);
            ctx.filter = "none";
            ctx.globalAlpha = 1;

            // Frosted milky layer.
            const gradient = ctx.createRadialGradient(
              bounds.centerX,
              bounds.centerY,
              Math.max(12, bounds.width * 0.2),
              bounds.centerX,
              bounds.centerY,
              Math.max(80, bounds.width * 0.9),
            );
            gradient.addColorStop(0, "rgba(250, 253, 255, 0.28)");
            gradient.addColorStop(1, "rgba(235, 242, 248, 0.15)");
            ctx.fillStyle = gradient;
            ctx.fillRect(0, 0, width, height);

            // Fine grain texture to mimic frosted glass.
            if (noiseCanvasRef.current) {
              ctx.globalAlpha = 0.22;
              ctx.drawImage(noiseCanvasRef.current, 0, 0);
              ctx.globalAlpha = 1;
            }

            // Subtle edge highlight.
            ctx.lineWidth = 1.4;
            ctx.strokeStyle = "rgba(245, 250, 255, 0.24)";
            ctx.stroke(facePath);
            ctx.restore();
          }
        }
      }

      rafRef.current = requestAnimationFrame(render);
    };

    rafRef.current = requestAnimationFrame(render);
    return () => {
      if (rafRef.current != null) {
        cancelAnimationFrame(rafRef.current);
      }
    };
  }, [enabled, videoRef]);

  return { canvasRef };
}
