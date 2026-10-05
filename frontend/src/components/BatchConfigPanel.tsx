import React from 'react';
import { Sparkles, Sliders, ShieldCheck } from 'lucide-react';
import { BatchCreateRequest } from '../types';

interface BatchConfigPanelProps {
  config: BatchCreateRequest;
  onChange: (config: BatchCreateRequest) => void;
  onStart: () => void;
  fileCount: number;
  isStarting: boolean;
  disabled: boolean;
}

export const BatchConfigPanel: React.FC<BatchConfigPanelProps> = ({
  config,
  onChange,
  onStart,
  fileCount,
  isStarting,
  disabled,
}) => {
  return (
    <div className="glass-panel rounded-2xl p-5 border border-white/10 space-y-5">
      <div className="flex items-center justify-between border-b border-white/10 pb-3">
        <div className="flex items-center gap-2">
          <Sliders className="w-5 h-5 text-cyan-400" />
          <h2 className="text-base font-bold text-white tracking-wide">Batch Upscale Options</h2>
        </div>
        <span className="text-xs text-slate-400 font-mono">
          {fileCount} {fileCount === 1 ? 'image' : 'images'} staged
        </span>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Preset Selection */}
        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
            Pipeline Preset
          </label>
          <div className="grid grid-cols-2 gap-1.5 p-1 rounded-xl bg-black/40 border border-white/10">
            <button
              type="button"
              onClick={() => onChange({ ...config, preset: 'ADOBE_STOCK', outputFormat: 'JPEG' })}
              className={`flex items-center justify-center gap-1.5 py-1.5 px-2 rounded-lg text-xs font-semibold transition-all ${
                config.preset === 'ADOBE_STOCK'
                  ? 'bg-gradient-to-r from-cyan-500 to-emerald-500 text-black shadow-glow-cyan'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>Adobe Stock</span>
            </button>
            <button
              type="button"
              onClick={() => onChange({ ...config, preset: 'CUSTOM' })}
              className={`py-1.5 px-2 rounded-lg text-xs font-semibold transition-all ${
                config.preset === 'CUSTOM'
                  ? 'bg-white/20 text-white border border-white/20'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              Custom
            </button>
          </div>
        </div>

        {/* Scale Multiplier */}
        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
            Upscale Scale
          </label>
          <div className="grid grid-cols-2 gap-1.5 p-1 rounded-xl bg-black/40 border border-white/10">
            <button
              type="button"
              onClick={() => onChange({ ...config, scale: 2 })}
              className={`py-1.5 px-2 rounded-lg text-xs font-semibold transition-all ${
                config.scale === 2
                  ? 'bg-cyan-500 text-black shadow-glow-cyan'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              2x Super-Res
            </button>
            <button
              type="button"
              onClick={() => onChange({ ...config, scale: 4 })}
              className={`py-1.5 px-2 rounded-lg text-xs font-semibold transition-all ${
                config.scale === 4
                  ? 'bg-cyan-500 text-black shadow-glow-cyan'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              4x Ultra-Res
            </button>
          </div>
        </div>

        {/* Neural Model */}
        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
            Super-Resolution Model
          </label>
          <select
            value={config.model}
            onChange={(e) => onChange({ ...config, model: e.target.value })}
            className="w-full py-2 px-3 rounded-xl bg-black/40 border border-white/10 text-xs font-medium text-white focus:outline-none focus:border-cyan-500"
          >
            <option value="general">Real-ESRGAN (General / Photo)</option>
            <option value="anime">Real-ESRGAN (Illustration / Anime)</option>
          </select>
        </div>

        {/* JPEG Quality */}
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
              Output Quality
            </label>
            <span className="text-xs font-mono text-cyan-400 font-bold">{config.quality}%</span>
          </div>
          <input
            type="range"
            min={85}
            max={100}
            value={config.quality}
            onChange={(e) => onChange({ ...config, quality: Number(e.target.value) })}
            className="w-full accent-cyan-400 cursor-pointer"
          />
        </div>
      </div>

      {/* Adobe Stock Compliance Rules Callout */}
      {config.preset === 'ADOBE_STOCK' && (
        <div className="p-3.5 rounded-xl bg-cyan-950/30 border border-cyan-500/20 text-xs text-slate-300 flex items-start gap-3">
          <ShieldCheck className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
          <div className="space-y-1">
            <span className="font-semibold text-white">Adobe Stock Submission Preset Activated:</span>
            <p className="text-slate-400 leading-relaxed">
              Outputs are automatically converted to <strong className="text-cyan-300">sRGB JPEG</strong>. Resolution is strictly bounded within <strong className="text-cyan-300">4.0 MP – 100.0 MP</strong> (proportionally downscaled if input scale exceeds 100 MP). File size is restricted to <strong className="text-cyan-300">&lt; 45 MB</strong> with intelligent quality fallback.
            </p>
          </div>
        </div>
      )}

      {/* Action Bar */}
      <div className="flex items-center justify-end gap-3 pt-2">
        <button
          type="button"
          disabled={disabled || fileCount === 0 || isStarting}
          onClick={onStart}
          className={`px-6 py-2.5 rounded-xl font-bold text-sm tracking-wide transition-all flex items-center gap-2 ${
            fileCount === 0 || disabled
              ? 'bg-slate-800 text-slate-500 cursor-not-allowed border border-white/5'
              : 'bg-gradient-to-r from-cyan-400 to-emerald-400 text-black hover:opacity-95 shadow-glow-cyan active:scale-[0.99]'
          }`}
        >
          <Sparkles className="w-4 h-4" />
          <span>{isStarting ? 'Uploading & Queuing...' : `Start Batch Upscaling (${fileCount} Images)`}</span>
        </button>
      </div>
    </div>
  );
};
