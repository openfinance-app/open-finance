/**
 * UserDropdownMenu Component
 * Task 4.3.13: User profile dropdown menu for TopBar
 * 
 * Provides user menu with profile, settings, help, and logout options.
 * Displays the user's profile image (if uploaded) or their initials as a fallback.
 */
import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { User, Settings, HelpCircle, LogOut, ChevronDown } from 'lucide-react';
import { useAuthContext } from '@/context/AuthContext';
import { cn } from '@/lib/utils';

/** Returns up to 2 initials from a username string. */
function getInitials(username: string): string {
  const parts = username.trim().split(/[\s._-]+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function UserDropdownMenu() {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const { user, clearAuth } = useAuthContext();
  const { t } = useTranslation('navigation');

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [isOpen]);

  // Close dropdown on escape key
  useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      return () => document.removeEventListener('keydown', handleEscape);
    }
  }, [isOpen]);

  const handleLogout = async () => {
    clearAuth();
    navigate('/login');
  };

  const menuItems = [
    {
      label: t('profile'),
      icon: User,
      onClick: () => {
        navigate('/profile');
        setIsOpen(false);
      },
    },
    {
      label: t('settings'),
      icon: Settings,
      onClick: () => {
        navigate('/settings');
        setIsOpen(false);
      },
    },
    {
      label: t('help'),
      icon: HelpCircle,
      onClick: () => {
        window.open('https://github.com/openfinance-app/open-finance/wiki', '_blank');
        setIsOpen(false);
      },
    },
    {
      label: t('logout'),
      icon: LogOut,
      onClick: handleLogout,
      danger: true,
    },
  ];

  const username = user?.username || 'User';
  const profileImage = user?.profileImage;
  const initials = getInitials(username);

  /** Shared avatar element used in both the trigger button and the dropdown header. */
  const AvatarSmall = (
    <div className="w-8 h-8 rounded-full overflow-hidden flex-shrink-0 bg-primary flex items-center justify-center">
      {profileImage ? (
        <img src={profileImage} alt={`${username}'s avatar`} className="w-full h-full object-cover" />
      ) : (
        <span className="text-xs font-bold text-background select-none">{initials}</span>
      )}
    </div>
  );

  const AvatarLarge = (
    <div className="w-10 h-10 rounded-full overflow-hidden flex-shrink-0 bg-primary flex items-center justify-center">
      {profileImage ? (
        <img src={profileImage} alt={`${username}'s avatar`} className="w-full h-full object-cover" />
      ) : (
        <span className="text-sm font-bold text-background select-none">{initials}</span>
      )}
    </div>
  );

  return (
    <div className="relative" ref={dropdownRef}>
      {/* User button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-surface transition-colors"
        aria-label={t('userMenu')}
        aria-expanded={isOpen}
        aria-haspopup="true"
      >
        {AvatarSmall}
        <span className="hidden md:block text-sm font-medium text-text-primary">
          {username}
        </span>
        <ChevronDown
          size={16}
          className={cn(
            'hidden md:block text-text-secondary transition-transform duration-200',
            isOpen && 'rotate-180'
          )}
        />
      </button>

      {/* Dropdown menu */}
      {isOpen && (
        <div
          className={cn(
            'absolute right-0 mt-2 w-56',
            'bg-surface border border-border rounded-lg shadow-lg',
            'py-2 z-50',
            'animate-in fade-in slide-in-from-top-2 duration-200'
          )}
        >
          {/* User info */}
          <div className="px-4 py-3 border-b border-border flex items-center gap-3">
            {AvatarLarge}
            <div className="min-w-0">
              <p className="text-sm font-semibold text-text-primary truncate">{username}</p>
              <p className="text-xs text-text-secondary truncate">{user?.email}</p>
            </div>
          </div>

          {/* Menu items */}
          <div className="py-1">
            {menuItems.map((item) => {
              const Icon = item.icon;
              return (
                <button
                  key={item.label}
                  onClick={item.onClick}
                  className={cn(
                    'w-full flex items-center gap-3 px-4 py-2.5',
                    'text-sm transition-colors',
                    item.danger
                      ? 'text-red-500 hover:bg-red-500/10'
                      : 'text-text-primary hover:bg-surface-elevated'
                  )}
                >
                  <Icon size={18} />
                  <span>{item.label}</span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

export default UserDropdownMenu;

