#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
文件SHA256哈希值和大小计算器 - GUI版本
专为Docker Registry测试设计

用法：
1. 直接运行: python file_hash_calculator.py
2. 点击选择文件按钮选择文件
"""

import hashlib
import os
import sys
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from pathlib import Path
import threading


def calculate_file_hash_and_size(file_path):
    """
    计算文件的SHA256哈希值和大小
    
    Args:
        file_path (str): 文件路径
        
    Returns:
        tuple: (sha256_hash, file_size, formatted_sha256)
    """
    try:
        file_path = Path(file_path)
        
        if not file_path.exists():
            raise FileNotFoundError(f"文件不存在: {file_path}")
        
        if not file_path.is_file():
            raise ValueError(f"路径不是文件: {file_path}")
        
        # 获取文件大小
        file_size = file_path.stat().st_size
        
        # 计算SHA256哈希值
        sha256_hash = hashlib.sha256()
        
        # 分块读取文件以节省内存
        with open(file_path, 'rb') as f:
            # 64KB块大小
            chunk_size = 65536
            while chunk := f.read(chunk_size):
                sha256_hash.update(chunk)
        
        hash_hex = sha256_hash.hexdigest()
        formatted_sha256 = f"sha256:{hash_hex}"
        
        return hash_hex, file_size, formatted_sha256
        
    except Exception as e:
        raise Exception(f"处理文件时出错: {e}")


class FileHashCalculatorGUI:
    """
    文件哈希值计算器GUI界面
    """
    
    def __init__(self):
        self.root = tk.Tk()
        self.root.title("文件哈希值计算器 - Docker Registry测试专用")
        self.root.geometry("700x600")
        self.root.configure(bg='#f0f0f0')
        
        # 设置窗口图标（如果有的话）
        try:
            self.root.iconbitmap(default='hash.ico')
        except:
            pass
        
        # 当前文件信息
        self.current_file = None
        self.hash_result = None
        
        self.setup_ui()
        
    def setup_ui(self):
        """
        设置用户界面
        """
        # 主标题
        title_frame = tk.Frame(self.root, bg='#f0f0f0')
        title_frame.pack(pady=20, padx=20, fill='x')
        
        title_label = tk.Label(
            title_frame,
            text="文件哈希值计算器",
            font=('微软雅黑', 16, 'bold'),
            bg='#f0f0f0',
            fg='#333333'
        )
        title_label.pack()
        
        subtitle_label = tk.Label(
            title_frame,
            text="Docker Registry测试专用",
            font=('微软雅黑', 10),
            bg='#f0f0f0',
            fg='#666666'
        )
        subtitle_label.pack()
        
        # 文件选择区域
        self.select_frame = tk.LabelFrame(
            self.root,
            text="文件选择",
            font=('微软雅黑', 12),
            bg='#ffffff',
            fg='#333333',
            relief='ridge',
            bd=2
        )
        self.select_frame.pack(pady=20, padx=20, fill='x')
        
        # 选择文件按钮
        select_btn = tk.Button(
            self.select_frame,
            text="选择文件",
            font=('微软雅黑', 12),
            bg='#4CAF50',
            fg='white',
            relief='flat',
            padx=30,
            pady=15,
            command=self.select_file
        )
        select_btn.pack(pady=20)
        
        # 当前选择的文件显示
        self.current_file_var = tk.StringVar(value="未选择文件")
        current_file_label = tk.Label(
            self.select_frame,
            textvariable=self.current_file_var,
            font=('微软雅黑', 10),
            bg='#ffffff',
            fg='#666666'
        )
        current_file_label.pack(pady=(0, 15))
        
        # 结果显示区域
        self.result_frame = tk.LabelFrame(
            self.root,
            text="计算结果",
            font=('微软雅黑', 12),
            bg='#ffffff',
            fg='#333333'
        )
        self.result_frame.pack(pady=(0, 20), padx=20, fill='x')
        
        # 文件信息
        info_frame = tk.Frame(self.result_frame, bg='#ffffff')
        info_frame.pack(fill='x', padx=10, pady=10)
        
        tk.Label(info_frame, text="文件名:", font=('微软雅黑', 10, 'bold'), bg='#ffffff').grid(row=0, column=0, sticky='w', pady=2)
        self.filename_var = tk.StringVar(value="未选择文件")
        tk.Label(info_frame, textvariable=self.filename_var, font=('微软雅黑', 10), bg='#ffffff', fg='#333333').grid(row=0, column=1, sticky='w', padx=(10, 0), pady=2)
        
        tk.Label(info_frame, text="文件大小:", font=('微软雅黑', 10, 'bold'), bg='#ffffff').grid(row=1, column=0, sticky='w', pady=2)
        self.filesize_var = tk.StringVar(value="")
        tk.Label(info_frame, textvariable=self.filesize_var, font=('微软雅黑', 10), bg='#ffffff', fg='#333333').grid(row=1, column=1, sticky='w', padx=(10, 0), pady=2)
        
        # SHA256结果
        hash_frame = tk.Frame(self.result_frame, bg='#ffffff')
        hash_frame.pack(fill='x', padx=10, pady=(0, 10))
        
        tk.Label(hash_frame, text="SHA256 (Docker格式):", font=('微软雅黑', 10, 'bold'), bg='#ffffff').pack(anchor='w')
        
        sha_frame = tk.Frame(hash_frame, bg='#ffffff')
        sha_frame.pack(fill='x', pady=(5, 0))
        
        self.sha_var = tk.StringVar(value="")
        self.sha_entry = tk.Entry(
            sha_frame,
            textvariable=self.sha_var,
            font=('Consolas', 10),
            state='readonly',
            bg='#f8f8f8',
            relief='flat',
            bd=1
        )
        self.sha_entry.pack(side='left', fill='x', expand=True)
        
        self.copy_sha_btn = tk.Button(
            sha_frame,
            text="复制",
            font=('微软雅黑', 9),
            bg='#2196F3',
            fg='white',
            relief='flat',
            padx=15,
            command=lambda: self.copy_to_clipboard(self.sha_var.get())
        )
        self.copy_sha_btn.pack(side='right', padx=(5, 0))
        
        # 文件大小结果
        tk.Label(hash_frame, text="文件大小 (字节):", font=('微软雅黑', 10, 'bold'), bg='#ffffff').pack(anchor='w', pady=(10, 0))
        
        size_frame = tk.Frame(hash_frame, bg='#ffffff')
        size_frame.pack(fill='x', pady=(5, 0))
        
        self.size_var = tk.StringVar(value="")
        self.size_entry = tk.Entry(
            size_frame,
            textvariable=self.size_var,
            font=('Consolas', 10),
            state='readonly',
            bg='#f8f8f8',
            relief='flat',
            bd=1
        )
        self.size_entry.pack(side='left', fill='x', expand=True)
        
        self.copy_size_btn = tk.Button(
            size_frame,
            text="复制",
            font=('微软雅黑', 9),
            bg='#2196F3',
            fg='white',
            relief='flat',
            padx=15,
            command=lambda: self.copy_to_clipboard(self.size_var.get())
        )
        self.copy_size_btn.pack(side='right', padx=(5, 0))
        
        # Content-Range结果
        tk.Label(hash_frame, text="Content-Range:", font=('微软雅黑', 10, 'bold'), bg='#ffffff').pack(anchor='w', pady=(10, 0))
        
        range_frame = tk.Frame(hash_frame, bg='#ffffff')
        range_frame.pack(fill='x', pady=(5, 0))
        
        self.range_var = tk.StringVar(value="")
        self.range_entry = tk.Entry(
            range_frame,
            textvariable=self.range_var,
            font=('Consolas', 10),
            state='readonly',
            bg='#f8f8f8',
            relief='flat',
            bd=1
        )
        self.range_entry.pack(side='left', fill='x', expand=True)
        
        self.copy_range_btn = tk.Button(
            range_frame,
            text="复制",
            font=('微软雅黑', 9),
            bg='#2196F3',
            fg='white',
            relief='flat',
            padx=15,
            command=lambda: self.copy_to_clipboard(self.range_var.get())
        )
        self.copy_range_btn.pack(side='right', padx=(5, 0))
        
        # 使用说明
        help_frame = tk.LabelFrame(
            self.root,
            text="Postman使用说明",
            font=('微软雅黑', 10),
            bg='#ffffff',
            fg='#333333'
        )
        help_frame.pack(pady=(0, 20), padx=20, fill='x')
        
        help_text = tk.Text(
            help_frame,
            height=4,
            font=('微软雅黑', 9),
            bg='#f8f8f8',
            fg='#333333',
            relief='flat',
            wrap='word',
            state='disabled'
        )
        help_text.pack(padx=10, pady=10, fill='x')
        
        help_content = """在 'Upload Chunk' 请求的 Headers 中设置：
  X-File-SHA256: 复制上面的SHA256值
  X-File-Size: 复制上面的文件大小
  Content-Range: 复制上面的Content-Range值
然后在Body中选择 'binary' 并选择你的文件"""
        
        help_text.config(state='normal')
        help_text.insert('1.0', help_content)
        help_text.config(state='disabled')
        
    
    def select_file(self):
        """
        通过文件对话框选择文件
        """
        file_path = filedialog.askopenfilename(
            title="选择要计算哈希值的文件",
            filetypes=[
                ("所有文件", "*.*"),
                ("文本文件", "*.txt"),
                ("图片文件", "*.jpg;*.png;*.gif"),
                ("压缩文件", "*.zip;*.tar;*.gz")
            ]
        )
        
        if file_path:
            self.process_file(file_path)
    
    def process_file(self, file_path):
        """
        处理选择的文件
        """
        try:
            # 更新当前文件显示
            self.current_file_var.set(f"正在计算 {Path(file_path).name}...")
            self.root.update()
            
            # 在后台线程计算哈希值
            def calculate_in_background():
                try:
                    hash_hex, file_size, formatted_sha256 = calculate_file_hash_and_size(file_path)
                    
                    # 在主线程更新UI
                    self.root.after(0, lambda: self.update_results(file_path, hash_hex, file_size, formatted_sha256))
                    
                except Exception as e:
                    self.root.after(0, lambda: self.show_error(f"计算文件哈希值时出错: {e}"))
            
            # 启动后台线程
            thread = threading.Thread(target=calculate_in_background, daemon=True)
            thread.start()
            
        except Exception as e:
            self.show_error(f"处理文件时出错: {e}")
    
    def update_results(self, file_path, hash_hex, file_size, formatted_sha256):
        """
        更新结果显示
        """
        file_name = Path(file_path).name
        file_size_formatted = self.format_file_size(file_size)
        content_range = f"0-{file_size-1}"
        
        # 更新文件信息
        self.filename_var.set(file_name)
        self.filesize_var.set(f"{file_size:,} 字节 ({file_size_formatted})")
        
        # 更新哈希值和其他信息
        self.sha_var.set(formatted_sha256)
        self.size_var.set(str(file_size))
        self.range_var.set(content_range)
        
        # 更新当前文件显示
        self.current_file_var.set(f"已处理: {file_name}")
        
        # 保存当前文件信息
        self.current_file = file_path
        self.hash_result = {
            'hash_hex': hash_hex,
            'file_size': file_size,
            'formatted_sha256': formatted_sha256,
            'content_range': content_range
        }
    
    def show_error(self, error_message):
        """
        显示错误信息
        """
        messagebox.showerror("错误", error_message)
        self.current_file_var.set("未选择文件")
    
    def copy_to_clipboard(self, text):
        """
        复制文本到剪贴板
        """
        if text:
            self.root.clipboard_clear()
            self.root.clipboard_append(text)
            self.root.update()  # 确保剪贴板更新
            
            # 显示复制成功的提示
            messagebox.showinfo("复制成功", f"已复制到剪贴板:\n{text}")
        else:
            messagebox.showwarning("警告", "没有可复制的内容")
    
    def format_file_size(self, size_bytes):
        """
        将字节数格式化为人类可读的大小
        """
        if size_bytes == 0:
            return "0 B"
        
        size_names = ["B", "KB", "MB", "GB", "TB"]
        i = 0
        size = float(size_bytes)
        
        while size >= 1024.0 and i < len(size_names) - 1:
            size /= 1024.0
            i += 1
        
        return f"{size:.1f} {size_names[i]}"

    def run(self):
        """
        运行GUI应用
        """
        self.root.mainloop()


def main():
    """
    主函数 - 启动GUI应用
    """
    try:
        # 创建并运行GUI应用
        app = FileHashCalculatorGUI()
        app.run()
            
    except Exception as e:
        print(f"❌ 启动应用时出错: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
