import { BASE_URL } from '../config/env';

/**
 * 【统一文件上传】
 *
 * <p>⚠️ `uni.uploadFile` 不会自动携带 `request.ts` 中注入的 satoken，
 * 必须显式传入 header，否则后端会因未登录而返回 401（表现为「上传静默失败」）。</p>
 *
 * @param tempPath 本地临时文件路径 (uni.chooseImage / 录音结果)
 * @param onProgress 进度回调 (0~100)
 * @returns 上传成功后的可访问 URL
 */
export function uploadFile(tempPath: string, onProgress?: (percent: number) => void): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const token = uni.getStorageSync('satoken') || '';

    const task = uni.uploadFile({
      url: BASE_URL + '/file/upload',
      filePath: tempPath,
      name: 'file',
      header: {
        'content-type': 'multipart/form-data',
        'satoken': token
      },
      success: (res: any) => {
        try {
          const json = JSON.parse(res.data);
          if (json && json.code === 200 && json.data) {
            resolve(json.data);
          } else {
            reject(new Error((json && json.message) ? json.message : '文件上传失败'));
          }
        } catch (e) {
          reject(new Error('文件上传响应解析失败'));
        }
      },
      fail: (err: any) => {
        reject(new Error((err && err.errMsg) ? err.errMsg : '文件上传失败'));
      }
    });

    if (onProgress && task && typeof task.onProgressUpdate === 'function') {
      task.onProgressUpdate((evt: any) => onProgress(evt.progress));
    }
  });
}
