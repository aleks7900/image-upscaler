import React from 'react';
import { X, Download, RotateCcw } from 'lucide-react';
import { ImageResponse } from '../types';
import { BeforeAfterCompare } from './BeforeAfterCompare';
import { StockValidationBadge } from './StockValidationBadge';
import { getImageInputUrl, getImageResultUrl } from '../api/client';

interface ImageDetailModalProps {
  image: ImageResponse | null;
  onClose: () => void;
  onRetry: (imageId: string) => void;
}

export const ImageDetailModal: React.FC<ImageDetailModalProps> = ({
  image,
  onClose,
  onRetry,
}) => {
  if (!image) return null;

  const inputUrl = getImageInputUrl(image.id);
  const isDone = image.status === 'COMPLETED' || image.status === 'STOCK_VALIDATION_FAILED' || Boolean(image.outputPath) || Boolean(image.outputWidth);
  const resultUrl = isDone ? getImageResultUrl(image.id) : inputUrl;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 bg-black/80 backdrop-blur-md">
      <div
        className="glass-panel rounded-3xl w-full max-w-5xl max-h-[90vh] overflow-y-auto border border-white/10 shadow-2xl flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-white/10">
          <div className="flex items-center gap-3">
            <h3 className="text-base font-bold text-white font-mono truncate max-w-md">
              {image.originalFilename}
            </h3>
            <StockValidationBadge image={image} />
          </div>

          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 space-y-6 flex-1">
          {/* Compare View */}
          <BeforeAfterCompare image={image} />

          {/* Technical Specs Breakdown */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
            <div className="p-3 rounded-xl bg-black/40 border border-white/5">
              <span className="text-slate-500 block text-[10px] uppercase font-sans">Input Geometry</span>
              <span className="text-white font-mono font-bold">{image.inputWidth} × {image.inputHeight}</span>
              <span className="text-slate-400 block text-[11px]">({image.inputMegapixels} Megapixels)</span>
            </div>

            <div className="p-3 rounded-xl bg-black/40 border border-white/5">
              <span className="text-cyan-400 block text-[10px] uppercase font-sans">Output Geometry</span>
              <span className="text-white font-mono font-bold">{image.outputWidth || '--'} × {image.outputHeight || '--'}</span>
              <span className="text-cyan-300 block text-[11px]">({image.outputMegapixels || '--'} Megapixels)</span>
            </div>

            <div className="p-3 rounded-xl bg-black/40 border border-white/5">
              <span className="text-slate-500 block text-[10px] uppercase font-sans">Color & Encoding</span>
              <span className="text-white font-mono font-bold">{image.colorProfile || 'sRGB'}</span>
              <span className="text-slate-400 block text-[11px]">{image.outputFormat || 'JPEG'}</span>
            </div>

            <div className="p-3 rounded-xl bg-black/40 border border-white/5">
              <span className="text-slate-500 block text-[10px] uppercase font-sans">Execution Duration</span>
              <span className="text-emerald-400 font-mono font-bold">{image.processingTimeMs ? `${image.processingTimeMs} ms` : '--'}</span>
              <span className="text-slate-400 block text-[11px]">{image.outputSize ? `${(image.outputSize / (1024*1024)).toFixed(2)} MB` : '--'}</span>
            </div>
          </div>
        </div>

        {/* Modal Footer Actions */}
        <div className="flex items-center justify-between p-5 border-t border-white/10 bg-black/20">
          <div>
            {(image.status === 'FAILED' || image.status === 'STOCK_VALIDATION_FAILED') && (
              <button
                type="button"
                onClick={() => {
                  onRetry(image.id);
                  onClose();
                }}
                className="px-4 py-2 rounded-xl bg-cyan-500/10 text-cyan-400 hover:bg-cyan-500/20 border border-cyan-500/30 text-xs font-semibold flex items-center gap-1.5 transition-all"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>Retry Image Inference</span>
              </button>
            )}
          </div>

          <div className="flex items-center gap-3 text-xs">
            <a
              href={inputUrl}
              download={image.originalFilename}
              className="px-4 py-2 rounded-xl text-slate-300 hover:text-white bg-white/5 hover:bg-white/10 border border-white/10 transition-colors flex items-center gap-1.5"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Download Original</span>
            </a>

            {isDone && (
              <a
                href={resultUrl}
                download={`upscaled_${image.originalFilename}`}
                className="px-4 py-2 rounded-xl bg-gradient-to-r from-cyan-400 to-emerald-400 text-black font-bold shadow-glow-cyan hover:opacity-95 transition-all flex items-center gap-1.5"
              >
                <Download className="w-3.5 h-3.5" />
                <span>Download Upscaled JPEG</span>
              </a>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
