import React, { useState } from 'react';
import { CheckCircle2, AlertTriangle, Info } from 'lucide-react';
import { ImageResponse } from '../types';

interface StockValidationBadgeProps {
  image: ImageResponse;
}

export const StockValidationBadge: React.FC<StockValidationBadgeProps> = ({ image }) => {
  const [showDetails, setShowDetails] = useState(false);
  const isStockReady = image.stockReady;

  const mp = image.outputMegapixels;
  const isMpValid = mp !== null && mp >= 4.0 && mp <= 100.0;
  const isFormatValid = image.outputFormat?.toUpperCase() === 'JPEG' || image.outputFormat?.toUpperCase() === 'JPG';
  const isColorValid = image.colorProfile?.toLowerCase().includes('srgb');
  const isSizeValid = image.outputSize !== null && image.outputSize <= 45 * 1024 * 1024;
  const sizeMb = image.outputSize ? (image.outputSize / (1024 * 1024)).toFixed(2) : '0';

  return (
    <div className="relative inline-block">
      <button
        type="button"
        onClick={() => setShowDetails(!showDetails)}
        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold tracking-wide transition-all ${
          isStockReady
            ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/20 shadow-glow-emerald'
            : 'bg-amber-500/10 text-amber-400 border border-amber-500/30 hover:bg-amber-500/20'
        }`}
      >
        {isStockReady ? (
          <>
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
            <span>ADOBE STOCK READY</span>
          </>
        ) : (
          <>
            <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />
            <span>STOCK VALIDATION FAILED</span>
          </>
        )}
        <Info className="w-3 h-3 opacity-60 ml-0.5" />
      </button>

      {/* Popover Breakdown */}
      {showDetails && (
        <div
          className="absolute z-30 bottom-full left-0 mb-2 w-72 p-3.5 rounded-xl glass-panel text-xs text-slate-200 shadow-2xl border border-white/10"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="font-semibold pb-2 mb-2 border-b border-white/10 flex items-center justify-between">
            <span>Adobe Stock Technical Audit</span>
            <span className={`text-[10px] uppercase px-1.5 py-0.5 rounded ${isStockReady ? 'bg-emerald-500/20 text-emerald-300' : 'bg-amber-500/20 text-amber-300'}`}>
              {isStockReady ? 'Pass' : 'Review'}
            </span>
          </div>

          <ul className="space-y-1.5">
            <li className="flex items-center justify-between">
              <span className="text-slate-400">Format:</span>
              <span className={isFormatValid ? 'text-emerald-400 font-medium' : 'text-rose-400'}>
                {isFormatValid ? '✓ JPEG' : `✕ ${image.outputFormat || 'Non-JPEG'}`}
              </span>
            </li>
            <li className="flex items-center justify-between">
              <span className="text-slate-400">Color Profile:</span>
              <span className={isColorValid ? 'text-emerald-400 font-medium' : 'text-rose-400'}>
                {isColorValid ? '✓ sRGB' : `✕ ${image.colorProfile || 'Unknown'}`}
              </span>
            </li>
            <li className="flex items-center justify-between">
              <span className="text-slate-400">Resolution:</span>
              <span className="text-slate-200">
                {image.outputWidth} × {image.outputHeight}
              </span>
            </li>
            <li className="flex items-center justify-between">
              <span className="text-slate-400">Megapixels (4–100 MP):</span>
              <span className={isMpValid ? 'text-emerald-400 font-medium' : 'text-rose-400'}>
                {isMpValid ? `✓ ${mp} MP` : `✕ ${mp} MP`}
              </span>
            </li>
            <li className="flex items-center justify-between">
              <span className="text-slate-400">File Size (&lt; 45 MB):</span>
              <span className={isSizeValid ? 'text-emerald-400 font-medium' : 'text-rose-400'}>
                {isSizeValid ? `✓ ${sizeMb} MB` : `✕ ${sizeMb} MB`}
              </span>
            </li>
          </ul>

          {image.error && (
            <div className="mt-2.5 pt-2 border-t border-white/10 text-[11px] text-amber-300">
              <p className="font-semibold">Diagnostic:</p>
              <p className="opacity-90">{image.error}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
