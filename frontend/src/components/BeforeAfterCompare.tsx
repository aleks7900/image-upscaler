import React, { useState } from 'react';
import { ImageResponse } from '../types';
import { getImageInputUrl, getImageResultUrl } from '../api/client';
import { Maximize2, SplitSquareVertical } from 'lucide-react';

interface BeforeAfterCompareProps {
  image: ImageResponse;
  onOpenZoom?: () => void;
}

export const BeforeAfterCompare: React.FC<BeforeAfterCompareProps> = ({ image, onOpenZoom }) => {
  const [sliderPos, setSliderPos] = useState(50);
  const [viewMode, setViewMode] = useState<'split' | 'side-by-side'>('split');

  const inputUrl = getImageInputUrl(image.id);
  const isDone = image.status === 'COMPLETED' || image.status === 'STOCK_VALIDATION_FAILED' || Boolean(image.outputPath) || Boolean(image.outputWidth);
  const resultUrl = isDone ? getImageResultUrl(image.id) : inputUrl;

  const inSizeMb = image.inputSize ? (image.inputSize / (1024 * 1024)).toFixed(2) : '0';
  const outSizeMb = image.outputSize ? (image.outputSize / (1024 * 1024)).toFixed(2) : '0';

  return (
    <div className="flex flex-col gap-3">
      {/* Mode toggle */}
      <div className="flex items-center justify-between text-xs text-slate-400">
        <div className="flex items-center gap-1.5 bg-black/30 p-1 rounded-lg border border-white/5">
          <button
            type="button"
            onClick={() => setViewMode('split')}
            className={`px-2 py-0.5 rounded text-[11px] font-medium transition-all ${
              viewMode === 'split' ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30' : 'text-slate-400 hover:text-white'
            }`}
          >
            Slider View
          </button>
          <button
            type="button"
            onClick={() => setViewMode('side-by-side')}
            className={`px-2 py-0.5 rounded text-[11px] font-medium transition-all ${
              viewMode === 'side-by-side' ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30' : 'text-slate-400 hover:text-white'
            }`}
          >
            Side-by-Side
          </button>
        </div>

        {onOpenZoom && (
          <button
            type="button"
            onClick={onOpenZoom}
            className="flex items-center gap-1 hover:text-cyan-400 transition-colors"
          >
            <Maximize2 className="w-3.5 h-3.5" />
            <span>Full Zoom</span>
          </button>
        )}
      </div>

      {viewMode === 'split' ? (
        /* Interactive Split Slider */
        <div className="relative w-full aspect-video rounded-xl overflow-hidden select-none bg-black/60 border border-white/10 group">
          {/* Upscaled Result (Bottom Layer) */}
          <img
            src={resultUrl}
            alt="Upscaled"
            className="absolute inset-0 w-full h-full object-contain"
          />

          {/* Original (Clipped Top Layer) */}
          <div
            className="absolute inset-0 overflow-hidden pointer-events-none"
            style={{ clipPath: `inset(0 ${100 - sliderPos}% 0 0)` }}
          >
            <img
              src={inputUrl}
              alt="Original"
              className="absolute inset-0 w-full h-full object-contain"
            />
          </div>

          {/* Divider Line */}
          <div
            className="absolute top-0 bottom-0 w-0.5 bg-cyan-400 cursor-ew-resize z-20 shadow-glow-cyan"
            style={{ left: `${sliderPos}%` }}
          >
            <div className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 w-6 h-6 rounded-full bg-cyan-500 text-black flex items-center justify-center shadow-lg">
              <SplitSquareVertical className="w-3.5 h-3.5" />
            </div>
          </div>

          {/* Interactive Range Input */}
          <input
            type="range"
            min="0"
            max="100"
            value={sliderPos}
            onChange={(e) => setSliderPos(Number(e.target.value))}
            className="absolute inset-0 w-full h-full opacity-0 cursor-ew-resize z-30"
          />

          {/* Badges */}
          <div className="absolute top-3 left-3 z-10 px-2 py-0.5 rounded bg-black/60 backdrop-blur-md text-[11px] font-semibold text-slate-300 border border-white/10">
            Original: {image.inputWidth}×{image.inputHeight}
          </div>
          <div className="absolute top-3 right-3 z-10 px-2 py-0.5 rounded bg-cyan-500/20 backdrop-blur-md text-[11px] font-semibold text-cyan-300 border border-cyan-500/30">
            Upscaled: {image.outputWidth}×{image.outputHeight}
          </div>
        </div>
      ) : (
        /* Side by Side Comparison */
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {/* Original Panel */}
          <div className="rounded-xl overflow-hidden bg-black/40 border border-white/10 p-2.5">
            <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
              <span className="font-semibold text-slate-200">Original</span>
              <span>{image.inputWidth} × {image.inputHeight} ({image.inputMegapixels} MP) • {inSizeMb} MB</span>
            </div>
            <div className="aspect-video rounded-lg overflow-hidden bg-black/60 flex items-center justify-center">
              <img src={inputUrl} alt="Original" className="w-full h-full object-contain" />
            </div>
          </div>

          {/* Upscaled Panel */}
          <div className="rounded-xl overflow-hidden bg-black/40 border border-cyan-500/20 p-2.5">
            <div className="flex items-center justify-between text-xs text-cyan-300 mb-2">
              <span className="font-semibold text-white">Upscaled Result</span>
              <span>{image.outputWidth} × {image.outputHeight} ({image.outputMegapixels} MP) • {outSizeMb} MB • sRGB</span>
            </div>
            <div className="aspect-video rounded-lg overflow-hidden bg-black/60 flex items-center justify-center">
              <img src={resultUrl} alt="Upscaled" className="w-full h-full object-contain" />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
