import React from 'react';
import { Cpu, HardDrive, Activity, Layers, Sparkles } from 'lucide-react';
import { GpuStatusResponse } from '../types';

interface HeaderProps {
  gpuStatus: GpuStatusResponse | null;
  onRefreshGpu?: () => void;
}

export const Header: React.FC<HeaderProps> = ({ gpuStatus }) => {
  const isCuda = gpuStatus?.cudaAvailable && gpuStatus?.available;
  const freeVramGb = gpuStatus?.freeVramMb ? (gpuStatus.freeVramMb / 1024).toFixed(1) : '0';
  const totalVramGb = gpuStatus?.totalVramMb ? (gpuStatus.totalVramMb / 1024).toFixed(1) : '0';
  const vramPercent = gpuStatus?.totalVramMb && gpuStatus.totalVramMb > 0
    ? Math.round(((gpuStatus.totalVramMb - gpuStatus.freeVramMb) / gpuStatus.totalVramMb) * 100)
    : 0;

  return (
    <header className="sticky top-0 z-40 border-b border-white/10 glass-panel backdrop-blur-xl">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          {/* Logo and Title */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-cyan-500 to-emerald-400 p-0.5 shadow-glow-cyan flex items-center justify-center">
              <div className="w-full h-full bg-[#0b0f19] rounded-[10px] flex items-center justify-center">
                <Sparkles className="w-5 h-5 text-cyan-400" />
              </div>
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-bold tracking-tight text-white flex items-center gap-2">
                  CUDA <span className="bg-gradient-to-r from-cyan-400 to-emerald-400 bg-clip-text text-transparent">UPSCALER</span>
                </h1>
                <span className="text-[10px] uppercase font-semibold px-2 py-0.5 rounded-full bg-cyan-500/10 text-cyan-300 border border-cyan-500/20">
                  Adobe Stock Pipeline
                </span>
              </div>
              <p className="text-xs text-slate-400">High-Throughput Super-Resolution & Technical Compliance</p>
            </div>
          </div>

          {/* GPU Status Indicators */}
          <div className="flex flex-wrap items-center gap-2 sm:gap-3 text-xs">
            {/* GPU Device */}
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white/5 border border-white/10">
              <Cpu className="w-4 h-4 text-emerald-400" />
              <div className="flex items-center gap-1.5">
                <span className="font-medium text-slate-300">GPU:</span>
                <span className="inline-block w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                <span className="text-white font-semibold truncate max-w-[140px] sm:max-w-[200px]">
                  {gpuStatus?.device || 'Detecting GPU...'}
                </span>
              </div>
            </div>

            {/* VRAM Meter */}
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white/5 border border-white/10">
              <HardDrive className="w-4 h-4 text-cyan-400" />
              <div>
                <span className="text-slate-400 mr-1.5">VRAM:</span>
                <span className="font-semibold text-slate-200">
                  {freeVramGb} / {totalVramGb} GB
                </span>
                <span className="text-[10px] text-cyan-400 ml-1">({vramPercent}% used)</span>
              </div>
            </div>

            {/* Worker Health Status */}
            <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/5 border border-white/10">
              <Activity className="w-4 h-4 text-emerald-400" />
              <span className="text-slate-400">Worker:</span>
              <span className={`font-semibold ${isCuda ? 'text-emerald-400' : 'text-amber-400'}`}>
                {isCuda ? '● READY' : 'OFFLINE'}
              </span>
            </div>

            {/* Queue Counter */}
            <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/5 border border-white/10">
              <Layers className="w-4 h-4 text-slate-400" />
              <span className="text-slate-400">Queue:</span>
              <span className="font-semibold text-white">{gpuStatus?.queuedJobs ?? 0}</span>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
};
