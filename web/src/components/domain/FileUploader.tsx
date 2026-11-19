import { useRef, useState } from "react";
import { Loader2, Trash2, UploadCloud } from "lucide-react";
import { uploadFile } from "@/api/file";
import { resolveFileUrl } from "@/api/file";
import { showError } from "@/lib/errors";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function FileUploader({
  value,
  onChange,
  accept = "image/*",
  label = "上传文件",
  hint,
  className
}: {
  value?: string;
  onChange: (url: string | undefined) => void;
  accept?: string;
  label?: string;
  hint?: string;
  className?: string;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);

  const isImage = accept.includes("image");
  const isAudio = accept.includes("audio");
  const preview = resolveFileUrl(value);

  const handleFile = async (file?: File) => {
    if (!file) return;
    setUploading(true);
    setProgress(0);
    try {
      const url = await uploadFile(file, setProgress);
      onChange(url);
    } catch (error) {
      showError(error, "文件上传失败");
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return (
    <div className={cn("space-y-2", className)}>
      {preview ? (
        <div className="relative inline-flex w-full max-w-sm items-center gap-3 rounded-lg border bg-muted/30 p-3">
          {isImage ? (
            <img src={preview} alt="preview" className="h-16 w-16 rounded-md object-cover" />
          ) : null}
          {isAudio ? <audio controls src={preview} className="h-9 w-full" /> : null}
          {!isImage && !isAudio ? (
            <a href={preview} target="_blank" rel="noreferrer" className="truncate text-sm text-primary">
              {preview}
            </a>
          ) : null}
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="ml-auto shrink-0 text-muted-foreground hover:text-destructive"
            onClick={() => onChange(undefined)}
            aria-label="移除文件"
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      ) : null}

      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="hidden"
        onChange={(event) => void handleFile(event.target.files?.[0])}
      />
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={uploading}
        onClick={() => inputRef.current?.click()}
      >
        {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <UploadCloud className="h-4 w-4" />}
        {uploading ? `上传中 ${progress}%` : label}
      </Button>
      {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  );
}

export function ImageUploader(props: Omit<Parameters<typeof FileUploader>[0], "accept">) {
  return <FileUploader {...props} accept="image/*" label={props.label || "上传图片"} />;
}

export function AudioUploader(props: Omit<Parameters<typeof FileUploader>[0], "accept">) {
  return <FileUploader {...props} accept="audio/*" label={props.label || "上传音频"} />;
}
