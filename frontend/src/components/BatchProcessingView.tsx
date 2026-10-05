import React from 'react';
import { Cpu, Zap, Clock, Hourglass, StopCircle, RefreshCw, CheckCircle, AlertTriangle } from 'lucide-react';
import { BatchResponse, ProgressEventDto } from '../types';

interface BatchProcessingViewProps {
  batch: BatchResponse;
  progress: ProgressEventDto | null;
  onCancel: () => void;
  isCancelling: boolean;
}

export const BatchProcessingView: React.FC<BatchProcessingViewProps> = ({
  batch,
  progress,
  onCancel,
  isCancelling,
}) => {
  const total = progress?.totalImages ?? batch.totalImages;
  const completed = progress?.completedImages ?? batch.completedImages;
  const failed = progress?.failedImages ?? batch.failedImages;
  const percent = progress?.progressPercent ?? (total > 0 ? Math.round(((completed + failed) / total) * 100) : 0);

  const formatSeconds = (sec: number | null | undefined): string => {
    if (sec === null || sec === undefined) return '--:--';
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  return (
    <div className="glass-panel rounded-3xl p-6 sm:p-8 border border-white/10 space-y-6 relative overflow-hidden">
      {/* Background ambient pulse */}
      <div className="absolute top-0 right-0 w-96 h-96 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none -mr-20 -mt-20" />

      {/* Header Info */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-white/10 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-cyan-400">
              Active CUDA Batch Execution
            </span>
            <span className="px-2 py-0.5 rounded-full bg-cyan-500/20 text-cyan-300 text-[10px] font-mono border border-cyan-500/30">
              {batch.preset} • {batch.scale}x
            </span>
          </div>
          <h2 className="text-lg sm:text-xl font-bold text-white tracking-tight font-mono mt-1">
            Batch: {batch.id}
          </h2>
        </div>

        <button
          type="button"
          onClick={onCancel}
          disabled={isCancelling || batch.status === 'COMPLETED' || batch.status === 'CANCELLED'}
          className="px-4 py-2 rounded-xl text-xs font-semibold text-rose-400 hover:text-white hover:bg-rose-500/20 border border-rose-500/30 transition-all flex items-center gap-2 w-fit disabled:opacity-40"
        >
          <StopCircle className="w-4 h-4" />
          <span>{isCancelling ? 'Cancelling...' : 'Cancel Batch'}</span>
        </button>
      </div>

      {/* Giant Progress Bar */}
      <div className="space-y-2">
        <div className="flex items-end justify-between">
          <div className="flex items-baseline gap-2">
            <span className="text-4xl sm:text-5xl font-black text-white tracking-tight">
              {percent}%
            </span>
            <span className="text-xs font-mono text-slate-400">
              {completed + failed} / {total} processed
            </span>
          </div>

          <div className="flex items-center gap-4 text-xs font-medium">
            <div className="flex items-center gap-1.5 text-emerald-400">
              <CheckCircle className="w-3.5 h-3.5" />
              <span>{completed} Completed</span>
            </div>
            {failed > 0 && (
              <div className="flex items-center gap-1.5 text-rose-400">
                <AlertTriangle className="w-3.5 h-3.5" />
                <span>{failed} Failed</span>
              </div>
            )}
          </div>
        </div>

        {/* Progress Bar Container */}
        <div className="h-4 w-full bg-black/60 rounded-full overflow-hidden p-0.5 border border-white/10">
          <div
            className="h-full rounded-full bg-gradient-to-r from-cyan-500 via-emerald-400 to-cyan-400 shadow-glow-cyan transition-all duration-300 relative overflow-hidden"
            style={{ width: `${Math.min(100, Math.max(2, percent))}%` }}
          >
            <div className="absolute inset-0 bg-white/20 animate-[shimmer_2s_infinite] bg-[linear-gradient(90deg,transparent_0%,rgba(255,255,255,0.4)_50%,transparent_100%)]" />
          </div>
        </div>
      </div>

      {/* Metrics Grid */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3 pt-2">
        {/* Current Image */}
        <div className="p-3 rounded-xl bg-black/40 border border-white/5 col-span-2 sm:col-span-1">
          <div className="text-[11px] text-slate-400 flex items-center gap-1 mb-1">
            <RefreshCw className="w-3 h-3 text-cyan-400 animate-spin" />
            <span>Current Asset</span>
          </div>
          <div className="text-xs font-mono font-semibold text-white truncate" title={progress?.currentImageFilename || 'Queuing inference...'}>
            {progress?.currentImageFilename || 'Initial setup...'}
          </div>
        </div>

        {/* GPU Device */}
        <div className="p-3 rounded-xl bg-black/40 border border-white/5">
          <div className="text-[11px] text-slate-400 flex items-center gap-1 mb-1">
            <Cpu className="w-3 h-3 text-emerald-400" />
            <span>GPU Device</span>
          </div>
          <div className="text-xs font-semibold text-slate-200 truncate">
            {progress?.gpuDevice || 'NVIDIA RTX'}
          </div>
        </div>

        {/* Throughput */}
        <div className="p-3 rounded-xl bg-black/40 border border-white/5">
          <div className="text-[11px] text-slate-400 flex items-center gap-1 mb-1">
            <Zap className="w-3 h-3 text-amber-400" />
            <span>Throughput</span>
          </div>
          <div className="text-xs font-mono font-semibold text-white">
            {progress?.throughput ? `${progress.throughput} img/s` : 'Calculating...'}
          </div>
        </div>

        {/* Elapsed Time */}
        <div className="p-3 rounded-xl bg-black/40 border border-white/5">
          <div className="text-[11px] text-slate-400 flex items-center gap-1 mb-1">
            <Clock className="w-3 h-3 text-slate-400" />
            <span>Elapsed</span>
          </div>
          <div className="text-xs font-mono font-semibold text-white">
            {formatSeconds(progress?.elapsedSeconds)}
          </div>
        </div>

        {/* Estimated Remaining */}
        <div className="p-3 rounded-xl bg-black/40 border border-white/5">
          <div className="text-[11px] text-slate-400 flex items-center gap-1 mb-1">
            <Hourglass className="w-3 h-3 text-cyan-400" />
            <span>ETA</span>
          </div>
          <div className="text-xs font-mono font-semibold text-cyan-300">
            {formatSeconds(progress?.estimatedRemainingSeconds)}
          </div>
        </div>
      </div>
    </div>
  );
};
