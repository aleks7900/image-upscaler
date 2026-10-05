import React, { useState } from 'react';
import { Download, RotateCcw, Eye, FileArchive, CheckCircle2, AlertTriangle, Filter } from 'lucide-react';
import { BatchResponse, ImageResponse } from '../types';
import { StockValidationBadge } from './StockValidationBadge';
import { getImageInputUrl, getImageResultUrl, getBatchZipDownloadUrl } from '../api/client';

interface ResultsGalleryProps {
  batch: BatchResponse;
  images: ImageResponse[];
  onRetryImage: (imageId: string) => void;
  onSelectImage: (image: ImageResponse) => void;
}

export const ResultsGallery: React.FC<ResultsGalleryProps> = ({
  batch,
  images,
  onRetryImage,
  onSelectImage,
}) => {
  const [filter, setFilter] = useState<'ALL' | 'READY' | 'FAILED'>('ALL');

  const filteredImages = images.filter((img) => {
    if (filter === 'READY') return img.stockReady;
    if (filter === 'FAILED') return img.status === 'FAILED' || img.status === 'STOCK_VALIDATION_FAILED';
    return true;
  });

  const zipDownloadUrl = getBatchZipDownloadUrl(batch.id);

  return (
    <div className="space-y-6">
      {/* Action Header & Summary Bar */}
      <div className="glass-panel rounded-2xl p-5 border border-white/10 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-bold text-white tracking-tight">Batch Results</h2>
            <span className="text-xs font-mono px-2 py-0.5 rounded bg-white/5 border border-white/10 text-slate-300">
              {images.length} assets
            </span>
          </div>
          <p className="text-xs text-slate-400 mt-0.5">
            Inspect super-resolution outputs, compliance audits, or download the submission archive.
          </p>
        </div>

        {/* Bulk Download Action */}
        <div className="flex items-center gap-3">
          <a
            href={zipDownloadUrl}
            className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-cyan-400 to-emerald-400 text-black text-xs font-bold shadow-glow-cyan hover:opacity-95 transition-all flex items-center gap-2"
          >
            <FileArchive className="w-4 h-4" />
            <span>Download Results ZIP</span>
          </a>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="flex items-center gap-2 text-xs">
        <span className="text-slate-500 flex items-center gap-1 mr-1">
          <Filter className="w-3.5 h-3.5" />
          <span>Filter:</span>
        </span>
        <button
          type="button"
          onClick={() => setFilter('ALL')}
          className={`px-3 py-1.5 rounded-lg font-semibold transition-all ${
            filter === 'ALL'
              ? 'bg-white/20 text-white border border-white/20'
              : 'text-slate-400 hover:text-white bg-black/20'
          }`}
        >
          All ({images.length})
        </button>
        <button
          type="button"
          onClick={() => setFilter('READY')}
          className={`px-3 py-1.5 rounded-lg font-semibold transition-all flex items-center gap-1 ${
            filter === 'READY'
              ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40'
              : 'text-slate-400 hover:text-white bg-black/20'
          }`}
        >
          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
          <span>Stock Ready ({images.filter((i) => i.stockReady).length})</span>
        </button>
        <button
          type="button"
          onClick={() => setFilter('FAILED')}
          className={`px-3 py-1.5 rounded-lg font-semibold transition-all flex items-center gap-1 ${
            filter === 'FAILED'
              ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40'
              : 'text-slate-400 hover:text-white bg-black/20'
          }`}
        >
          <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />
          <span>Failed / Review ({images.filter((i) => i.status === 'FAILED' || i.status === 'STOCK_VALIDATION_FAILED').length})</span>
        </button>
      </div>

      {/* Image Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredImages.map((img) => {
          const inputUrl = getImageInputUrl(img.id);
          const isDone = img.status === 'COMPLETED' || img.status === 'STOCK_VALIDATION_FAILED' || Boolean(img.outputPath) || Boolean(img.outputWidth);
          const resultUrl = isDone ? getImageResultUrl(img.id) : inputUrl;

          return (
            <div
              key={img.id}
              onClick={() => onSelectImage(img)}
              className="glass-panel rounded-2xl overflow-hidden border border-white/10 hover:border-cyan-500/40 transition-all flex flex-col group cursor-pointer"
            >
              {/* Image Preview Header with Hover Zoom icon */}
              <div className="relative aspect-video bg-black/60 overflow-hidden">
                <img
                  src={resultUrl}
                  alt={img.originalFilename}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                />
                <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-2">
                  <div className="px-3 py-1.5 rounded-full bg-cyan-500 text-black text-xs font-bold flex items-center gap-1.5 shadow-lg">
                    <Eye className="w-3.5 h-3.5" />
                    <span>Compare & Zoom</span>
                  </div>
                </div>

                {/* Status Overlay Badge */}
                <div className="absolute top-2.5 left-2.5">
                  <StockValidationBadge image={img} />
                </div>
              </div>

              {/* Card Body */}
              <div className="p-4 space-y-3 flex-1 flex flex-col justify-between">
                <div>
                  <h3 className="text-xs font-bold text-white truncate font-mono" title={img.originalFilename}>
                    {img.originalFilename}
                  </h3>

                  {/* Resolution Comparison Grid */}
                  <div className="mt-2.5 grid grid-cols-2 gap-2 text-[11px] p-2 rounded-xl bg-black/30 border border-white/5 font-mono">
                    <div>
                      <span className="text-slate-500 block text-[9px] uppercase font-sans">Original</span>
                      <span className="text-slate-300 font-semibold">{img.inputWidth}×{img.inputHeight}</span>
                      <span className="text-slate-500 block text-[9px]">({img.inputMegapixels} MP)</span>
                    </div>
                    <div>
                      <span className="text-cyan-400 block text-[9px] uppercase font-sans">Upscaled ({batch.scale}x)</span>
                      <span className="text-white font-bold">{img.outputWidth || '--'}×{img.outputHeight || '--'}</span>
                      <span className="text-cyan-400 block text-[9px]">({img.outputMegapixels || '--'} MP) • {img.colorProfile || 'sRGB'}</span>
                    </div>
                  </div>

                  {img.error && (
                    <div className="mt-2 text-[11px] text-amber-300/90 leading-tight">
                      <span className="font-semibold">Notice:</span> {img.error}
                    </div>
                  )}
                </div>

                {/* Actions */}
                <div className="flex items-center justify-between pt-2 border-t border-white/5" onClick={(e) => e.stopPropagation()}>
                  <div className="text-[10px] text-slate-500 font-mono">
                    {img.processingTimeMs ? `${img.processingTimeMs}ms` : ''}
                  </div>

                  <div className="flex items-center gap-2">
                    {img.status === 'FAILED' || img.status === 'STOCK_VALIDATION_FAILED' ? (
                      <button
                        type="button"
                        onClick={() => onRetryImage(img.id)}
                        className="px-2.5 py-1 rounded-lg text-xs font-semibold text-cyan-400 hover:bg-cyan-500/10 border border-cyan-500/20 flex items-center gap-1 transition-all"
                      >
                        <RotateCcw className="w-3 h-3" />
                        <span>Retry</span>
                      </button>
                    ) : null}

                    {isDone && (
                      <a
                        href={resultUrl}
                        download={`upscaled_${img.originalFilename}`}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
                        title="Download Result"
                      >
                        <Download className="w-4 h-4" />
                      </a>
                    )}
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
